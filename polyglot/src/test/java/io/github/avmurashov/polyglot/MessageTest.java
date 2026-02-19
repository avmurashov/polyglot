package io.github.avmurashov.polyglot;

import org.junit.jupiter.api.Test;

import java.text.MessageFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageTest {
    @Test
    void constructorThrowsNpeIfMessageFormatIsNull() {
        assertThatException().isThrownBy(() -> new Message(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void formatPassesParametersAsIsToMessageFormat() {
        final var parameters = new Object[] {1, "test", true};
        final var result = "only test result is true";

        final var messageFormat = mock(MessageFormat.class);
        when(messageFormat.format(parameters)).thenReturn(result);

        final var message = new Message(messageFormat);
        assertThat(message.format(parameters)).isEqualTo(result);
    }
}