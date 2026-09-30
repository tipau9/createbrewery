package com.createbrewery.entity;

import java.util.Set;

/** A club's guest list, as a bouncer reads it from a book. Plain Java, so it is unit-testable. */
final class GuestList {
    private GuestList() {}

    /** One name per line, or split by commas and semicolons; trimmed and lower-cased. */
    static void add(Set<String> into, String text) {
        for (String line : text.split("[\r\n,;]+")) {
            String trimmed = line.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                into.add(trimmed);
            }
        }
    }
}
