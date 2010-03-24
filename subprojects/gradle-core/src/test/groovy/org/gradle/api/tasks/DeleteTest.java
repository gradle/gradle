/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.internal.ConventionTask;
import org.gradle.util.TestFile;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class DeleteTest extends AbstractConventionTaskTest {
    private Delete delete;

    @Before
    public void setUp() {
        super.setUp();
        delete = createTask(Delete.class);
    }

    public ConventionTask getTask() {
        return delete;
    }

    @Test
    public void defaultValues() {
        assertTrue(delete.getDelete().isEmpty());
    }

    @Test
    public void deletesDirectory() throws IOException {
        TestFile dir = tmpDir.getDir();
        dir.file("somefile").createFile();

        delete.delete(dir);
        delete.execute();

        dir.assertDoesNotExist();
        assertTrue(delete.getDidWork());
    }

    @Test
    public void deletesFile() throws IOException {
        TestFile dir = tmpDir.getDir();
        TestFile file = dir.file("somefile");
        file.createFile();

        delete.delete(file);
        delete.execute();

        file.assertDoesNotExist();
        assertTrue(delete.getDidWork());
    }

    @Test
    public void deletesMultipleTargets() throws IOException {
        TestFile file = tmpDir.getDir().file("somefile").createFile();
        TestFile dir = tmpDir.getDir().file("somedir").createDir();
        dir.file("sub/child").createFile();

        delete.delete(file);
        delete.delete(dir);
        delete.execute();

        file.assertDoesNotExist();
        dir.assertDoesNotExist();
        assertTrue(delete.getDidWork());
    }

    @Test
    public void didWorkIsFalseWhenNothingDeleted() throws IOException {
        TestFile dir = tmpDir.file("unknown");
        dir.assertDoesNotExist();

        delete.delete(dir);
        delete.execute();

        assertFalse(delete.getDidWork());
    }
}
