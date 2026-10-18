package com.portfolio.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import com.portfolio.knowledge.domain.DocumentException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void keepsPageAndHeadingBoundariesWithStableOrdinalsAndChecksums() {
        var sections =
                List.of(
                        new TextExtractor.Section(1, "Account", "Reset password."),
                        new TextExtractor.Section(2, "Security", "Use a strong password."));
        var chunks = chunker.split(sections);
        assertThat(chunks).isEqualTo(chunker.split(sections));
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).ordinal()).isZero();
        assertThat(chunks.get(1).ordinal()).isEqualTo(1);
        assertThat(chunks.get(1).pageNumber()).isEqualTo(2);
        assertThat(chunks.get(1).heading()).isEqualTo("Security");
        assertThat(chunks.get(1).contentChecksum()).matches("[a-f0-9]{64}");
        var changed =
                chunker.split(
                        List.of(
                                new TextExtractor.Section(
                                        3, "Security", "Use a strong password.")));
        assertThat(changed.getFirst().contentChecksum())
                .isNotEqualTo(chunks.get(1).contentChecksum());
    }

    @Test
    void splitsLongParagraphWithRealTokenLimitsAndOverlappingText() {
        var text = "reset password now ".repeat(1500).strip();
        var chunks = chunker.split(List.of(new TextExtractor.Section(null, "Guide", text)));
        var encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        assertThat(chunks).hasSizeGreaterThan(3);
        for (int i = 0; i < chunks.size(); i++) {
            var current = chunks.get(i);
            assertThat(current.ordinal()).isEqualTo(i);
            assertThat(current.tokenCount()).isBetween(1, DocumentChunker.MAX_TOKENS);
            assertThat(current.tokenCount())
                    .isEqualTo(encoding.countTokensOrdinary(current.content()));
            if (i > 0) {
                var previous = chunks.get(i - 1).content();
                int overlap = 0;
                for (int length = 1;
                        length < Math.min(previous.length(), current.content().length());
                        length++) {
                    if (previous.endsWith(current.content().substring(0, length))) {
                        overlap = length;
                    }
                }
                assertThat(overlap).isPositive();
            }
        }
    }

    @Test
    void preservesUnicodeAndSpecialTokenLikeInputWithoutReplacementCharacters() {
        var text = "Ti\u1ebfng Vi\u1ec7t \uD83D\uDD10 \u6F22\u5B57 <|endoftext|> ".repeat(400);
        var chunks = chunker.split(List.of(new TextExtractor.Section(null, null, text)));
        assertThat(chunks)
                .allSatisfy(
                        chunk -> {
                            assertThat(chunk.content()).doesNotContain("\uFFFD");
                            assertThat(text).contains(chunk.content());
                            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(1000);
                        });
    }

    @Test
    void prefersParagraphBoundariesNearTarget() {
        var paragraph = "guide ".repeat(400).strip();
        var chunks =
                chunker.split(
                        List.of(
                                new TextExtractor.Section(
                                        1,
                                        null,
                                        paragraph + "\n\n" + paragraph + "\n\n" + paragraph)));
        assertThat(chunks.getFirst().content()).isEqualTo(paragraph + "\n\n" + paragraph);
    }

    @Test
    void windowsCoverOriginalTextWithoutGapsAndOverlapByOneHundredTokens() {
        var text =
                java.util.stream.IntStream.range(0, 2000)
                        .mapToObj(i -> "item" + String.format(java.util.Locale.ROOT, "%04d", i))
                        .collect(java.util.stream.Collectors.joining(" "));
        var chunks = chunker.split(List.of(new TextExtractor.Section(null, null, text)));
        var encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        int coveredUntil = 0;
        for (var chunk : chunks) {
            int start = text.indexOf(chunk.content());
            assertThat(start).isGreaterThanOrEqualTo(0);
            assertThat(start).isLessThanOrEqualTo(coveredUntil);
            if (coveredUntil > 0) {
                assertThat(encoding.countTokensOrdinary(text.substring(start, coveredUntil)))
                        .isBetween(98, 100);
            }
            coveredUntil = start + chunk.content().length();
        }
        assertThat(coveredUntil).isEqualTo(text.length());
    }

    @Test
    void rejectsNoText() {
        assertThatThrownBy(
                        () -> chunker.split(List.of(new TextExtractor.Section(null, null, "  "))))
                .isInstanceOfSatisfying(
                        DocumentException.class,
                        exception -> assertThat(exception.code()).isEqualTo("DOCUMENT_EMPTY"));
    }
}
