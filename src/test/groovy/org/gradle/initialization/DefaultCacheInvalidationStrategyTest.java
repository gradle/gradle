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
package org.gradle.initialization;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.gradle.util.WrapUtil;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.gradle.CacheUsage;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class DefaultCacheInvalidationStrategyTest {
    private DefaultCacheInvalidationStrategy cacheInvalidationStrategy;

    private File projectDir;
    private File buildResolverDir;

    private List<String> projectFiles = WrapUtil.toList("src",
            "src/main/resources/properties.txt",
            "mystuff/test/resources/org/test.xml",
            "mystuff",
            "somefile.txt",
            "build.gradle");

    private List<String> ignoreFiles = WrapUtil.toList("build/somefile",
            "build",
            "src/main/resources/.svn/properties.txt",
            ".svn",
            ".gradle/somefile",
            ".gradle");

    private File artifactFile;
    
    @Before
    public void setUp() {
        cacheInvalidationStrategy = new DefaultCacheInvalidationStrategy();
        projectDir = HelperUtil.makeNewTestDir("buildSrc");
        buildResolverDir = HelperUtil.makeNewTestDir("buildResolver");
        artifactFile = new File(projectDir, "build/buildSrc.jar");
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }
    
    @Test
    public void isValidWithNoNewerFiles() throws IOException {
        createTestFile();
        assertTrue(cacheInvalidationStrategy.isValid(artifactFile, projectDir));
    }

    @Test
    public void isValidWithNewerFiles() throws IOException, InterruptedException {
        createTestFile();
        for (File projectFile : getFiles(projectFiles)) {
            projectFile.setLastModified(artifactFile.lastModified() + 1000);
            assertFalse(cacheInvalidationStrategy.isValid(artifactFile, projectDir));
            artifactFile.setLastModified(projectFile.lastModified());
        }
    }

    @Test
    public void isValidWithIgnoreFiles() throws IOException, InterruptedException {
        createTestFile();
        for (File ignoreFile : getFiles(ignoreFiles)) {
            ignoreFile.setLastModified(artifactFile.lastModified() + 1000);
            assertTrue(cacheInvalidationStrategy.isValid(artifactFile, projectDir));
            artifactFile.setLastModified(ignoreFile.lastModified());
        }
    }

    private void createTestFile() throws IOException {
        for (File file : getFiles(allTestFiles())) {
            file.mkdirs();
            file.createNewFile();
        }
        artifactFile.createNewFile();
    }

    private List<File> getFiles(List<String> fileNames) {
        ArrayList<File> files = new ArrayList<File>();
        for (String fileName : fileNames) {
            files.add(new File(projectDir, fileName));
        }
        return files;
    }

    private List<String> allTestFiles() {
        return GUtil.addLists(projectFiles, ignoreFiles);
    }
}
