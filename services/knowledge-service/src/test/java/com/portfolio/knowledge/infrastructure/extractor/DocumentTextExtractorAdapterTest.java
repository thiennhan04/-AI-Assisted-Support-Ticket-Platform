package com.portfolio.knowledge.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.knowledge.domain.DocumentException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorAdapterTest {
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final DocumentTextExtractorAdapter extractor = new DocumentTextExtractorAdapter();

    @Test
    void extractsPdfTextWithRealPageNumbers() throws Exception {
        try (var document = new PDDocument();
                var output = new ByteArrayOutputStream()) {
            for (var text : new String[] {"Account guide", "Reset password"}) {
                var page = new PDPage();
                document.addPage(page);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(30, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            var sections = extractor.extract(output.toByteArray(), "application/pdf");
            assertThat(sections).hasSize(2);
            assertThat(sections.get(0).pageNumber()).isEqualTo(1);
            assertThat(sections.get(1).pageNumber()).isEqualTo(2);
            assertThat(sections.get(1).text()).isEqualTo("Reset password");
            assertThat(sections.get(0).heading()).isNull();
        }
    }

    @Test
    void extractsDocxHeadingsParagraphsAndTablesWithoutInventingPages() throws Exception {
        try (var document = new XWPFDocument();
                var output = new ByteArrayOutputStream()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Account guide");
            document.createParagraph().createRun().setText("Reset your password.");
            document.createTable(1, 1).getRow(0).getCell(0).setText("Contact support");
            var next = document.createParagraph();
            next.setStyle("Heading2");
            next.createRun().setText("Security");
            document.createParagraph().createRun().setText("Use a strong password.");
            document.write(output);
            var sections = extractor.extract(output.toByteArray(), DOCX);
            assertThat(sections).hasSize(2);
            assertThat(sections.get(0).heading()).isEqualTo("Account guide");
            assertThat(sections.get(0).text()).contains("Reset your password.", "Contact support");
            assertThat(sections.get(1).heading()).isEqualTo("Security");
            assertThat(sections).allMatch(section -> section.pageNumber() == null);
        }
    }

    @Test
    void normalizesUtf8TextAndKeepsMarkdownHeadingBoundaries() {
        var bytes =
                "\uFEFF# Account\r\n\r\n  Cafe\u0301\t guide  \r\n\r\n\r\n## Help\r\nContact support"
                        .getBytes(StandardCharsets.UTF_8);
        var sections = extractor.extract(bytes, "text/plain");
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("Account");
        assertThat(sections.get(0).text()).isEqualTo("# Account\n\nCaf\u00e9 guide");
        assertThat(sections.get(1).heading()).isEqualTo("Help");
    }

    @Test
    void rejectsBlankTextAndImageOnlyPdfAsEmpty() throws Exception {
        assertCode(" \n\t".getBytes(StandardCharsets.UTF_8), "text/plain", "DOCUMENT_EMPTY");
        try (var document = new PDDocument();
                var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            assertCode(output.toByteArray(), "application/pdf", "DOCUMENT_EMPTY");
        }
    }

    @Test
    void rejectsEncryptedPdfWithStableErrorCode() throws Exception {
        try (var document = new PDDocument();
                var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.protect(
                    new StandardProtectionPolicy(
                            "owner-secret", "user-secret", new AccessPermission()));
            document.save(output);
            assertCode(output.toByteArray(), "application/pdf", "DOCUMENT_ENCRYPTED");
        }
    }

    @Test
    void rejectsEncryptedDocxWithStableErrorCode() throws Exception {
        try (var document = new XWPFDocument();
                var office = new org.apache.poi.poifs.filesystem.POIFSFileSystem();
                var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Private guide");
            var info =
                    new org.apache.poi.poifs.crypt.EncryptionInfo(
                            org.apache.poi.poifs.crypt.EncryptionMode.agile);
            var encryptor = info.getEncryptor();
            encryptor.confirmPassword("test-password");
            try (var encrypted = encryptor.getDataStream(office)) {
                document.write(encrypted);
            }
            office.writeFilesystem(output);
            assertCode(output.toByteArray(), DOCX, "DOCUMENT_ENCRYPTED");
        }
    }

    @Test
    void rejectsMalformedPdfDocxAndUtf8Safely() {
        assertCode(
                "%PDF-broken".getBytes(StandardCharsets.UTF_8),
                "application/pdf",
                "DOCUMENT_INVALID");
        assertCode(new byte[] {'P', 'K', 0, 0}, DOCX, "DOCUMENT_INVALID");
        assertCode(new byte[] {(byte) 0xc3, 0x28}, "text/plain", "DOCUMENT_INVALID");
        assertCode(new byte[] {1}, "image/png", "UNSUPPORTED_DOCUMENT_TYPE");
    }

    @Test
    void rejectsExpandedZipAndExcessiveText() throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            try (var zip = new ZipOutputStream(output)) {
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write(new byte[8 * 1024 * 1024 + 1]);
                zip.closeEntry();
            }
            assertCode(output.toByteArray(), DOCX, "DOCUMENT_TOO_LARGE");
        }
        assertCode(
                "a"
                        .repeat(DocumentTextExtractorAdapter.MAX_TEXT_CHARACTERS + 1)
                        .getBytes(StandardCharsets.UTF_8),
                "text/plain",
                "DOCUMENT_TOO_LARGE");
    }

    private void assertCode(byte[] bytes, String mime, String code) {
        assertThatThrownBy(() -> extractor.extract(bytes, mime))
                .isInstanceOfSatisfying(
                        DocumentException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo(code);
                            assertThat(exception.status()).isEqualTo(422);
                        });
    }
}
