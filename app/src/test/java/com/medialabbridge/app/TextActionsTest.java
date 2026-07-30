package com.medialabbridge.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TextActionsTest {
    @Test
    public void selectedOrAllReturnsSelectionWhenPresent() {
        assertEquals("mundo", TextActions.selectedOrAll("hola mundo", 5, 10));
    }

    @Test
    public void selectedOrAllReturnsEverythingWithoutSelection() {
        assertEquals("hola mundo", TextActions.selectedOrAll("hola mundo", 4, 4));
    }

    @Test
    public void selectedOrAllAcceptsReversedSelection() {
        assertEquals("hola", TextActions.selectedOrAll("hola mundo", 4, 0));
    }

    @Test
    public void normalizedBaseUrlAddsHttpAndRemovesTrailingSlash() {
        assertEquals(
                "http://192.168.1.20:8765",
                TextActions.normalizedBaseUrl(" 192.168.1.20:8765/ ")
        );
    }
}
