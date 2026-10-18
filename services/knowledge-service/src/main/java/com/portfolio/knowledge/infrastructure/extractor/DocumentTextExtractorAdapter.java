package com.portfolio.knowledge.infrastructure.extractor;

import com.portfolio.knowledge.application.DocumentService;
import com.portfolio.knowledge.application.TextExtractor;
import com.portfolio.knowledge.domain.DocumentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextExtractorAdapter implements TextExtractor {
    public static final int MAX_TEXT_CHARACTERS = 2_000_000;
    private static final int MAX_PDF_PAGES = 500;
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public List<Section> extract(byte[] bytes, String mimeType) {
        if (bytes.length > DocumentService.MAX_SIZE_BYTES) {
            throw failure("DOCUMENT_TOO_LARGE", "Document exceeds the extraction limit");
        }
        try {
            // Confirm encryption rather than treating every legacy OLE file as encrypted.
            if (bytes.length >= 4
                    && bytes[0] == (byte) 0xd0
                    && bytes[1] == (byte) 0xcf
                    && bytes[2] == (byte) 0x11
                    && bytes[3] == (byte) 0xe0) {
                try (var container = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
                    if (container.getRoot().hasEntry("EncryptedPackage")) {
                        throw failure(
                                "DOCUMENT_ENCRYPTED", "Encrypted documents are not supported");
                    }
                    throw failure(
                            "UNSUPPORTED_DOCUMENT_TYPE",
                            "Legacy Office documents are not supported");
                }
            }
            var sections =
                    switch (mimeType) {
                        case "application/pdf" -> pdf(bytes);
                        case DOCX -> docx(bytes);
                        case "text/plain" -> text(bytes);
                        default ->
                                throw failure(
                                        "UNSUPPORTED_DOCUMENT_TYPE",
                                        "Document type is not supported");
                    };
            var normalized = new ArrayList<Section>();
            int total = 0;
            for (var section : sections) {
                var content = normalize(section.text());
                var heading = section.heading() == null ? null : normalize(section.heading());
                if (heading != null && heading.length() > 500) {
                    heading = heading.substring(0, 500);
                }
                total += content.length();
                if (total > MAX_TEXT_CHARACTERS) {
                    throw failure("DOCUMENT_TOO_LARGE", "Extracted text exceeds the limit");
                }
                if (!content.isBlank()) {
                    normalized.add(new Section(section.pageNumber(), heading, content));
                }
            }
            if (normalized.isEmpty()) {
                throw failure("DOCUMENT_EMPTY", "Document contains no extractable text");
            }
            return List.copyOf(normalized);
        } catch (InvalidPasswordException exception) {
            throw failure("DOCUMENT_ENCRYPTED", "Encrypted documents are not supported");
        } catch (DocumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DocumentException(
                    "DOCUMENT_INVALID", 422, "Document cannot be read", exception);
        }
    }

    private List<Section> pdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw failure("DOCUMENT_ENCRYPTED", "Encrypted documents are not supported");
            }
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw failure("DOCUMENT_TOO_LARGE", "PDF exceeds the page limit");
            }
            var sections = new ArrayList<Section>();
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setParagraphEnd("\n\n");
            int remaining = MAX_TEXT_CHARACTERS;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                var writer = new LimitedTextWriter(remaining);
                stripper.writeText(document, writer);
                var content = writer.toString();
                remaining -= content.length();
                // PDF has reliable page numbers, but arbitrary visual headings are not inferred.
                sections.add(new Section(page, null, content));
            }
            return sections;
        }
    }

    private List<Section> docx(byte[] bytes) throws IOException {
        verifyZipLimits(bytes);
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var sections = new ArrayList<Section>();
            var content = new LimitedTextWriter(MAX_TEXT_CHARACTERS);
            String heading = null;
            int total = 0;
            for (var element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    var style = paragraph.getStyle();
                    if (style != null && style.matches("(?i)heading[ _]?[1-9]")) {
                        sections.add(new Section(null, heading, content.toString()));
                        total += content.toString().length();
                        content = new LimitedTextWriter(MAX_TEXT_CHARACTERS - total);
                        heading = paragraph.getText();
                    }
                    content.write(paragraph.getText());
                    content.write("\n\n");
                } else if (element instanceof XWPFTable table) {
                    content.write(table.getText());
                    content.write("\n\n");
                }
            }
            // DOCX pagination depends on the renderer; never fabricate page numbers.
            sections.add(new Section(null, heading, content.toString()));
            return sections;
        }
    }

    private List<Section> text(byte[] bytes) throws IOException {
        var raw = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        if (raw.length() > MAX_TEXT_CHARACTERS) {
            throw failure("DOCUMENT_TOO_LARGE", "Extracted text exceeds the limit");
        }
        var sections = new ArrayList<Section>();
        var content = new StringBuilder();
        String heading = null;
        for (var line :
                raw.replace("\uFEFF", "")
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .split("\n", -1)) {
            if (line.matches("^#{1,6}\\s+.+")) {
                sections.add(new Section(null, heading, content.toString()));
                content.setLength(0);
                heading = line.replaceFirst("^#{1,6}\\s+", "");
            }
            content.append(line).append('\n');
        }
        sections.add(new Section(null, heading, content.toString()));
        return sections;
    }

    private void verifyZipLimits(byte[] bytes) throws IOException {
        long total = 0;
        int entries = 0;
        var buffer = new byte[8192];
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) {
                if (++entries > 2000) {
                    throw failure("DOCUMENT_TOO_LARGE", "DOCX contains too many ZIP entries");
                }
                long entrySize = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    entrySize += read;
                    if (entrySize > 8L * 1024 * 1024 || total > 32L * 1024 * 1024) {
                        throw failure("DOCUMENT_TOO_LARGE", "Expanded DOCX exceeds the limit");
                    }
                }
            }
        }
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\uFEFF", "")
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "")
                .replaceAll("[\\p{Zs}\\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static DocumentException failure(String code, String message) {
        return new DocumentException(code, 422, message);
    }

    private static final class LimitedTextWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        private final int limit;

        private LimitedTextWriter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(char[] characters, int offset, int length) {
            if (text.length() + length > limit) {
                throw failure("DOCUMENT_TOO_LARGE", "Extracted text exceeds the limit");
            }
            text.append(characters, offset, length);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
