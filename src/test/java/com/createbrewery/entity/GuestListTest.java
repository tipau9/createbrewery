package com.createbrewery.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GuestListTest {

    @Test
    void guestListTrimmingAndCaseInsensitivity() {
        Set<String> list = new HashSet<>();
        GuestList.add(list, "Alice\n  Bob  \nCHARLIE, Dave;Eve");

        assertEquals(Set.of("alice", "bob", "charlie", "dave", "eve"), list);
    }

    @Test
    void blankLinesAndSeparatorsAddNoGuests() {
        Set<String> list = new HashSet<>();
        GuestList.add(list, " \n,;\r\n ");

        assertTrue(list.isEmpty());
    }
}
