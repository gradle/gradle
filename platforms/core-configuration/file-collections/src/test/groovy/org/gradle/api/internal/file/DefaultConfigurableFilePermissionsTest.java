/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.file;

import org.gradle.api.file.ConfigurableUserClassFilePermissions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("OctalInteger")
public class DefaultConfigurableFilePermissionsTest {

    @Test
    public void directoryInitializedWithSensibleDefaults() {
        DefaultConfigurableFilePermissions permissions = newPermission(true);
        assertPermissions(permissions.getUser(), true, true, true);
        assertPermissions(permissions.getGroup(), true, false, true);
        assertPermissions(permissions.getOther(), true, false, true);
        assertEquals(0755, permissions.toUnixNumeric());
    }

    @Test
    public void fileInitializedWithSensibleDefaults() {
        DefaultConfigurableFilePermissions permissions = newPermission(false);
        assertPermissions(permissions.getUser(), true, true, false);
        assertPermissions(permissions.getGroup(), true, false, false);
        assertPermissions(permissions.getOther(), true, false, false);
        assertEquals(0644, permissions.toUnixNumeric());
    }

    @Test
    public void unixPermissionInNumericNotation() {
        for (int u = 0; u <=7; u++) {
            for (int g = 0; g <= 7; g++) {
                for (int o = 0; o <= 7; o++) {
                    String numericNotation = String.format("%d%d%d", u, g, o);
                    int unixNumeric = u * 64 + g * 8 + o;
                    assertEquals(unixNumeric, newUnixPermission(numericNotation).toUnixNumeric());
                    assertEquals(unixNumeric, newUnixPermission(unixNumeric).toUnixNumeric());
                }
            }
        }

        assertInvalidUnixNumericPermissionRejected(Integer.MIN_VALUE);
        assertInvalidUnixNumericPermissionRejected(-100);
        assertInvalidUnixNumericPermissionRejected(-10);
        assertInvalidUnixNumericPermissionRejected(-1);
        assertInvalidUnixNumericPermissionRejected(512);
        assertInvalidUnixNumericPermissionRejected(513);
        assertInvalidUnixNumericPermissionRejected(1024);
        assertInvalidUnixNumericPermissionRejected(Integer.MAX_VALUE);
    }

    @Test
    public void unixPermissionInNumericNotation_whitespaceIsTrimmed() {
        assertEquals(0754, newUnixPermission("  754   ").toUnixNumeric());
    }

    @Test
    public void unixPermissionInNumericNotation_octalLiteralIsAccepted() {
        assertEquals(0754, newUnixPermission("0754").toUnixNumeric());
    }

    @Test
    public void unixPermissionInSymbolicNotation() {
        for (int u = 0; u <=7; u++) {
            for (int g = 0; g <= 7; g++) {
                for (int o = 0; o <= 7; o++) {
                    String symbolicNotation = String.format("%s%s%s", toUnixSymbolic(u), toUnixSymbolic(g), toUnixSymbolic(o));
                    int mode = u * 64 + g * 8 + o;
                    assertEquals(mode, newUnixPermission(symbolicNotation).toUnixNumeric());
                }
            }
        }
    }

    @Test
    public void unixPermissionInSymbolicNotation_whitespaceIsTrimmed() {
        assertEquals(0754, newUnixPermission("  rwxr-xr--   ").toUnixNumeric());
    }

    @Test
    public void unixPermissionBadValues() {
        assertInvalidUnixPermission("", "'' isn't a proper Unix permission. Trimmed length must be either 3 (for numeric notation) or 9 (for symbolic notation).");
        assertInvalidUnixPermission(" ", "' ' isn't a proper Unix permission. Trimmed length must be either 3 (for numeric notation) or 9 (for symbolic notation).");
        assertInvalidUnixPermission("   ", "'   ' isn't a proper Unix permission. Trimmed length must be either 3 (for numeric notation) or 9 (for symbolic notation).");
        assertInvalidUnixPermission("         ", "'         ' isn't a proper Unix permission. Trimmed length must be either 3 (for numeric notation) or 9 (for symbolic notation).");
        assertInvalidUnixPermission("812", "'812' isn't a proper Unix permission. Can't be parsed as octal number.");
        assertInvalidUnixPermission("790", "'790' isn't a proper Unix permission. Can't be parsed as octal number.");
        assertInvalidUnixPermission("649", "'649' isn't a proper Unix permission. Can't be parsed as octal number.");
        assertInvalidUnixPermission("64 9", "'64 9' isn't a proper Unix permission. Trimmed length must be either 3 (for numeric notation) or 9 (for symbolic notation).");
        assertInvalidUnixPermission("|wxrwxrwx", "'|wxrwxrwx' isn't a proper Unix permission. '|' is not a valid Unix permission READ flag, must be 'r' or '-'.");
        assertInvalidUnixPermission("rwx|wxrwx", "'rwx|wxrwx' isn't a proper Unix permission. '|' is not a valid Unix permission READ flag, must be 'r' or '-'.");
        assertInvalidUnixPermission("rwxrwx|wx", "'rwxrwx|wx' isn't a proper Unix permission. '|' is not a valid Unix permission READ flag, must be 'r' or '-'.");
        assertInvalidUnixPermission("r|xrwxrwx", "'r|xrwxrwx' isn't a proper Unix permission. '|' is not a valid Unix permission WRITE flag, must be 'w' or '-'.");
        assertInvalidUnixPermission("rwxr|xrwx", "'rwxr|xrwx' isn't a proper Unix permission. '|' is not a valid Unix permission WRITE flag, must be 'w' or '-'.");
        assertInvalidUnixPermission("rwxrwxr|x", "'rwxrwxr|x' isn't a proper Unix permission. '|' is not a valid Unix permission WRITE flag, must be 'w' or '-'.");
        assertInvalidUnixPermission("rw|rwxrwx", "'rw|rwxrwx' isn't a proper Unix permission. '|' is not a valid Unix permission EXECUTE flag, must be 'x' or '-'.");
        assertInvalidUnixPermission("rwxrw|rwx", "'rwxrw|rwx' isn't a proper Unix permission. '|' is not a valid Unix permission EXECUTE flag, must be 'x' or '-'.");
        assertInvalidUnixPermission("rwxrwxrw|", "'rwxrwxrw|' isn't a proper Unix permission. '|' is not a valid Unix permission EXECUTE flag, must be 'x' or '-'.");
        assertInvalidUnixPermission("rwxrw xrwx", "'rwxrw xrwx' isn't a proper Unix permission. Trimmed length must be either 3 (for numeric notation) or 9 (for symbolic notation).");
    }

    private static DefaultConfigurableFilePermissions newUnixPermission(String unixPermission) {
        DefaultConfigurableFilePermissions permissions = newPermission(false);
        permissions.unix(unixPermission);
        return permissions;
    }

    private static DefaultConfigurableFilePermissions newUnixPermission(int unixNumeric) {
        DefaultConfigurableFilePermissions permissions = newPermission(false);
        permissions.unix(unixNumeric);
        return permissions;
    }

    private static void assertInvalidUnixNumericPermissionRejected(int unixNumeric) {
        DefaultConfigurableFilePermissions permissions;
        try {
            permissions = newPermission(false);
            permissions.unix(unixNumeric);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) {
            assertEquals(unixNumeric + " is not a valid unix numeric permission from the range of [0, 512)", e.getMessage());
        }
    }

    private static DefaultConfigurableFilePermissions newPermission(boolean isDirectory) {
        return new DefaultConfigurableFilePermissions(DefaultConfigurableFilePermissions.getDefaultUnixNumeric(isDirectory));
    }

    private static void assertPermissions(ConfigurableUserClassFilePermissions permission, boolean read, boolean write, boolean execute) {
        assertEquals("READ permission incorrect", read, permission.getRead());
        assertEquals("WRITE permission incorrect", write, permission.getWrite());
        assertEquals("EXECUTE permission incorrect", execute, permission.getExecute());
    }

    private static void assertInvalidUnixPermission(String unixPermission, String errorMessage) {
        try {
            newUnixPermission(unixPermission).toUnixNumeric();
            fail("Expected exception not thrown!");
        } catch (Exception e) {
            assertEquals(errorMessage, e.getMessage());
        }
    }

    private static String toUnixSymbolic(int unitNumeric) {
        return String.format("%s%s%s", isRead(unitNumeric) ? "r" : "-", isWrite(unitNumeric) ? "w" : "-", isExecute(unitNumeric) ? "x" : "-");
    }

    private static boolean isRead(int unixNumeric) {
        return (unixNumeric & 4) >> 2 == 1;
    }

    private static boolean isWrite(int unixNumeric) {
        return (unixNumeric & 2) >> 1 == 1;
    }

    private static boolean isExecute(int unixNumeric) {
        return (unixNumeric & 1) == 1;
    }

}
