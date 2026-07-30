package com.medialabbridge.app;

final class TextActions {
    private TextActions() {
    }

    static String selectedOrAll(CharSequence source, int selectionStart, int selectionEnd) {
        if (source == null || source.length() == 0) {
            return "";
        }

        int start = safeInsertionIndex(source, selectionStart);
        int end = safeInsertionIndex(source, selectionEnd);
        if (start == end) {
            return source.toString();
        }

        return source.subSequence(Math.min(start, end), Math.max(start, end)).toString();
    }

    static int safeInsertionIndex(CharSequence source, int requestedIndex) {
        int length = source == null ? 0 : source.length();
        if (requestedIndex < 0) {
            return length;
        }
        return Math.min(requestedIndex, length);
    }

    static String normalizedBaseUrl(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Escribe la dirección del PC.");
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
