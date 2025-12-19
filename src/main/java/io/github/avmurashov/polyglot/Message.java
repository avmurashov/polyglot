package io.github.avmurashov.polyglot;

import org.apache.commons.lang3.Validate;

import java.text.MessageFormat;

/**
 * Polyglot message.
 */
class Message {
    private final MessageFormat messageFormat;

    /**
     * Construct the polyglot message with the given {@code MessageFormat}.
     *
     * @param messageFormat Polyglot message format.
     */
    Message(MessageFormat messageFormat) {
        this.messageFormat = Validate.notNull(messageFormat, "messageFormat");
    }

    /**
     * Apply actual parameters to the message.
     *
     * @param parameters Actual parameters.
     * @return Result of the actual parameters' application.
     */
    String format(Object[] parameters) {
        return messageFormat.format(parameters);
    }
}
