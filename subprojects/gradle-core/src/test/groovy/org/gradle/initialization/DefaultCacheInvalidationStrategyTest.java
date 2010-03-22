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
package org.gradle.initialization;

import org.gradle.util.GUtil;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.WrapUtil;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultCacheInvalidationStrategyTest {
    private DefaultCacheInvalidationStrategy cacheInvalidationStrategy;

    private File projectDir;

    private List<String> projectFiles = WrapUtil.toList("src/main/resources/properties.txt",
            "mystuff/test/resources/org/test.xml",
            "somefile.txt",
            "build.gradle");

    private List<String> projectDirs = WrapUtil.toList("src",
            "mystuff");

    private List<String> ignoreFiles = WrapUtil.toList("build/somefile",
            "src/main/resources/.svn/properties.txt",
            ".gradle/somefile");

    private List<String> ignoreDirs = WrapUtil.toList("build",
            ".svn",
            ".gradle");

    private long timestamp;

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        cacheInvalidationStrategy = new DefaultCacheInvalidationStrategy();
        projectDir = tmpDir.createDir("buildSrc");
        createTestFile();
    }

    @Test
    public void isValidWithNoNewerFiles() throws IOException {
        assertTrue(cacheInvalidationStrategy.isValid(timestamp, projectDir));
    }

    @Test
    public void isNotValidWithNewerFiles() throws IOException, InterruptedException {
        for (File projectFile : getFiles(GUtil.addLists(projectFiles, projectDirs))) {
            projectFile.setLastModified(timestamp + 1000);
            assertFalse(cacheInvalidationStrategy.isValid(timestamp, projectDir));
            timestamp = projectFile.lastModified();
        }
    }

    @Test
    public void isValidWithIgnoreFiles() throws IOException, InterruptedException {
        for (File ignoreFile : getFiles(GUtil.addLists(ignoreFiles, ignoreDirs))) {
            ignoreFile.setLastModified(timestamp + 1000);
            assertTrue(cacheInvalidationStrategy.isValid(timestamp, projectDir));
            timestamp = ignoreFile.lastModified();
        }
    }

    private void createTestFile() throws IOException {
        createTestDirs();
        createTestFiles();
    }

    private void createTestDirs() {
        for (Object fileName : GUtil.addLists(projectDirs, ignoreDirs)) {
            (new File(projectDir, (String) fileName)).mkdirs();
        }
    }

    private void createTestFiles() throws IOException {
        for (Object fileName : GUtil.addLists(projectFiles, ignoreFiles)) {
            File file = new File(projectDir, (String) fileName);
            file.getParentFile().mkdirs();
            file.createNewFile();
            timestamp = Math.max(timestamp, file.lastModified());
        }
    }

    private List<File> getFiles(List<String> fileNames) {
        ArrayList<File> files = new ArrayList<File>();
        for (String fileName : fileNames) {
            files.add(new File(projectDir, fileName));
        }
        return files;
    }


}
