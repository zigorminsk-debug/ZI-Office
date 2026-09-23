package com.docreader.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Тесты сравнения номеров версий (автообновление). */
public class UpdateManagerTest {

    @Test public void newer_patch() {
        assertTrue(UpdateManager.isNewer("2.24", "2.23"));
        assertTrue(UpdateManager.isNewer("2.10", "2.9"));
        assertTrue(UpdateManager.isNewer("10.0", "9.9"));
        assertTrue(UpdateManager.isNewer("2.23.1", "2.23"));
        assertTrue(UpdateManager.isNewer("2.24-beta", "2.23"));
    }

    @Test public void same_isNotNewer() {
        assertFalse(UpdateManager.isNewer("2.23", "2.23"));
        assertFalse(UpdateManager.isNewer("2.23", "2.23.0"));
    }

    @Test public void older_isNotNewer() {
        assertFalse(UpdateManager.isNewer("2.22", "2.23"));
        assertFalse(UpdateManager.isNewer("2.23.1", "2.23.5"));
        assertFalse(UpdateManager.isNewer("2.2", "2.23"));
    }

    @Test public void vPrefix_isHandled() {
        assertTrue(UpdateManager.isNewer("v2.24", "2.23"));
        assertFalse(UpdateManager.isNewer("v2.23", "2.23"));
    }

    @Test public void garbage_doesNotCrash() {
        assertFalse(UpdateManager.isNewer("abc", "2.23"));
        assertTrue(UpdateManager.isNewer("2.24", "abc"));
        assertFalse(UpdateManager.isNewer("", "2.23"));
        // пустая локальная версия трактуется как самая старая → обновляемся
        assertTrue(UpdateManager.isNewer("2.23", ""));
        assertFalse(UpdateManager.isNewer("", ""));
    }
}
