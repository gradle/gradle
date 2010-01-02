/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.util.TestFile;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class CleanTest extends AbstractConventionTaskTest {
    private Clean clean;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        clean = createTask(Clean.class);
    }

    public ConventionTask getTask() {
        return clean;
    }

    @Test
    public void testClean() {
        assertNull(clean.getDir());
    }

    @Test
    public void testExecute() throws IOException {
        TestFile dir = tmpDir.getDir();
        clean.setDir(dir);
        dir.file("somefile");
        clean.execute();
        assertFalse(clean.getDir().exists());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testExecuteWithNullDir() {
        clean.clean();
    }

}
