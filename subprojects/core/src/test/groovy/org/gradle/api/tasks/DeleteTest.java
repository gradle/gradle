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
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.WrapUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class DeleteTest extends AbstractConventionTaskTest {
    private Delete delete;

    @Before
    public void setUp() {
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
    public void didWorkIsTrueWhenSomethingGetsDeleted() throws IOException {
        TestFile file = tmpDir.createFile("someFile");

        delete.delete(file);
        delete.execute();

        assertTrue(delete.getDidWork());
        assertFalse(file.exists());
    }

    @Test
    public void didWorkIsFalseWhenNothingDeleted() throws IOException {
        delete.delete("does-not-exist");
        delete.execute();

        assertFalse(delete.getDidWork());
    }

    @Test
    public void getTargetFilesAndMultipleTargets() throws IOException {
        delete.delete("someFile");
        delete.delete(new File("someOtherFile"));
        delete.getTargetFiles();
        assertThat(delete.getDelete(), equalTo(WrapUtil.<Object>toSet("someFile", new File("someOtherFile"))));
        assertThat(delete.getTargetFiles().getFiles(), equalTo(getProject().files(delete.getDelete()).getFiles()));
    }
}
