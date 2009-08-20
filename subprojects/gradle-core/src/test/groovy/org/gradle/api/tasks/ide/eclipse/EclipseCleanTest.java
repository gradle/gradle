/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.tasks.ide.eclipse;

import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.junit.After;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class EclipseCleanTest extends AbstractTaskTest {
    private EclipseClean eclipseClean;
    private File projectDir;

    public AbstractTask getTask() {
        return eclipseClean;
    }

    @Before
    public void setUp() {
        super.setUp();
        projectDir = HelperUtil.makeNewTestDir();
        eclipseClean = createTask(EclipseClean.class);
        try {
            createEclipseFiles();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createEclipseFiles() throws IOException {
        new File(projectDir, EclipseProject.PROJECT_FILE_NAME).createNewFile();
        new File(projectDir, EclipseClasspath.CLASSPATH_FILE_NAME).createNewFile();
        new File(projectDir, EclipseWtp.WTP_FILE_DIR).mkdirs();
        new File(projectDir, EclipseWtp.WTP_FILE_DIR + "/" + EclipseWtp.WTP_FILE_NAME).createNewFile();
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void cleanWithExistingFiles() {
        eclipseClean.execute();
        checkDeletion();
    }

    @Test
    public void cleanWithOneNonExistingFiles() {
        GFileUtils.deleteQuietly(new File(projectDir, EclipseProject.PROJECT_FILE_NAME));
        eclipseClean.execute();
        checkDeletion();
    }

    private void checkDeletion() {
        assertFalse(new File(projectDir, EclipseProject.PROJECT_FILE_NAME).exists());
        assertFalse(new File(projectDir, EclipseClasspath.CLASSPATH_FILE_NAME).exists());
        assertFalse(new File(projectDir, EclipseWtp.WTP_FILE_DIR + "/" + EclipseWtp.WTP_FILE_NAME).exists());
    }
}
