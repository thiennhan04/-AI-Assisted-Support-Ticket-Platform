package com.portfolio.knowledge.application;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import com.portfolio.knowledge.domain.Chunk;
import com.portfolio.knowledge.domain.DocumentException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {
    public static final String CHUNKING_VERSION = "cl100k-700-100-v1";
    public static final int TARGET_TOKENS = 700;
    public static final int MAX_TOKENS = 1000;
    public static final int OVERLAP_TOKENS = 100;
    private static final int MAX_CHUNKS = 5000;
    private final Encoding encoding =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    public List<Chunk> split(List<TextExtractor.Section> sections) {
        var chunks = new ArrayList<Chunk>();
        for (var section : sections) {
            var remaining = section.text().strip();
            while (!remaining.isBlank()) {
                var encoded = encoding.encodeOrdinary(remaining, MAX_TOKENS + 1);
                String content;
                int end;
                if (!encoded.isTruncated() && encoded.getTokens().size() <= MAX_TOKENS) {
                    content = remaining;
                    end = remaining.length();
                } else {
                    var prefix =
                            encoding.decode(
                                    encoding.encodeOrdinary(remaining, TARGET_TOKENS).getTokens());
                    end = prefix.length();
                    // Prefer a paragraph boundary near the target; never cross page/heading
                    // sections.
                    int nextParagraph = remaining.indexOf("\n\n", end);
                    if (nextParagraph >= 0
                            && encoding.countTokensOrdinary(remaining.substring(0, nextParagraph))
                                    <= MAX_TOKENS) {
                        end = nextParagraph;
                    } else {
                        int previousParagraph = prefix.lastIndexOf("\n\n");
                        if (previousParagraph > 0
                                && encoding.countTokensOrdinary(
                                                prefix.substring(0, previousParagraph))
                                        >= TARGET_TOKENS / 2) {
                            end = previousParagraph;
                        }
                    }
                    content = remaining.substring(0, end).strip();
                }
                if (chunks.size() >= MAX_CHUNKS) {
                    throw new DocumentException(
                            "DOCUMENT_TOO_LARGE", 422, "Document exceeds the chunk limit");
                }
                int ordinal = chunks.size();
                var checksum = checksum(ordinal, section.pageNumber(), section.heading(), content);
                chunks.add(
                        new Chunk(
                                ordinal,
                                section.pageNumber(),
                                section.heading(),
                                content,
                                checksum,
                                encoding.countTokensOrdinary(content)));
                if (end == remaining.length()) {
                    break;
                }
                var overlap = overlap(remaining.substring(0, end));
                remaining = overlap + remaining.substring(end);
            }
        }
        if (chunks.isEmpty()) {
            throw new DocumentException(
                    "DOCUMENT_EMPTY", 422, "Document contains no extractable text");
        }
        return List.copyOf(chunks);
    }

    private String overlap(String content) {
        var tokens = encoding.encodeOrdinary(content);
        for (int start = Math.max(0, tokens.size() - OVERLAP_TOKENS);
                start < tokens.size();
                start++) {
            var suffixTokens = new IntArrayList();
            for (int i = start; i < tokens.size(); i++) {
                suffixTokens.add(tokens.get(i));
            }
            var suffix = encoding.decode(suffixTokens);
            // A token boundary can fall inside a UTF-8 character. Move forward until it is safe.
            if (content.endsWith(suffix)) {
                return suffix;
            }
        }
        return "";
    }

    private String checksum(int ordinal, Integer page, String heading, String content) {
        // Length-prefixed fields avoid ambiguous metadata/content concatenation.
        var canonical =
                CHUNKING_VERSION
                        + "\n"
                        + ordinal
                        + "\n"
                        + page
                        + "\n"
                        + (heading == null ? -1 : heading.length())
                        + ":"
                        + (heading == null ? "" : heading)
                        + "\n"
                        + content.length()
                        + ":"
                        + content;
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
