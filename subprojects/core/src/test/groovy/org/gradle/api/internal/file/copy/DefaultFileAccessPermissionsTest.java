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

package org.gradle.api.internal.file.copy;

import org.gradle.api.file.FileAccessPermission;
import org.gradle.util.TestUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultFileAccessPermissionsTest {

    @Test
    public void directoryInitializedWithSensibleDefaults() {
        DefaultFileAccessPermissions permissions = new DefaultFileAccessPermissions(TestUtil.objectFactory(), true);
        assertPermissions(permissions.getUser(), true, true, true);
        assertPermissions(permissions.getGroup(), true, false, true);
        assertPermissions(permissions.getOther(), true, false, true);
        assertEquals(0755, permissions.toMode());
    }

    @Test
    public void fileInitializedWithSensibleDefaults() {
        DefaultFileAccessPermissions permissions = new DefaultFileAccessPermissions(TestUtil.objectFactory(), false);
        assertPermissions(permissions.getUser(), true, true, false);
        assertPermissions(permissions.getGroup(), true, false, false);
        assertPermissions(permissions.getOther(), true, false, false);
        assertEquals(0644, permissions.toMode());
    }

    private static void assertPermissions(FileAccessPermission permission, boolean read, boolean write, boolean execute) {
        assertEquals("READ permission incorrect", read, permission.getRead().get());
        assertEquals("WRITE permission incorrect", write, permission.getWrite().get());
        assertEquals("EXECUTE permission incorrect", execute, permission.getExecute().get());
    }

}
