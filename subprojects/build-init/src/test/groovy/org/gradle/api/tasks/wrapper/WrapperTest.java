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

package org.gradle.api.tasks.wrapper;

import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.WrapUtil;
import org.gradle.wrapper.GradleWrapperMain;
import org.gradle.wrapper.WrapperExecutor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

public class WrapperTest extends AbstractTaskTest {

    private Wrapper wrapper;
    private String targetWrapperJarPath;
    private TestFile expectedTargetWrapperJar;
    private File expectedTargetWrapperProperties;
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Before
    public void setUp() {
        wrapper = createTask(Wrapper.class);
        wrapper.setGradleVersion("1.0");
        targetWrapperJarPath = "gradle/wrapper";
        expectedTargetWrapperJar = new TestFile(getProject().getProjectDir(),
                targetWrapperJarPath + "/gradle-wrapper.jar");
        expectedTargetWrapperProperties = new File(getProject().getProjectDir(),
                targetWrapperJarPath + "/gradle-wrapper.properties");
        new File(getProject().getProjectDir(), targetWrapperJarPath).mkdirs();
        wrapper.setDistributionPath("somepath");
    }

    public AbstractTask getTask() {
        return wrapper;
    }

    @Test
    public void testWrapperDefaults() {
        wrapper = createTask(Wrapper.class);
        assertEquals(new File(getProject().getProjectDir(), "gradle/wrapper/gradle-wrapper.jar"), wrapper.getJarFile());
        assertEquals(new File(getProject().getProjectDir(), "gradlew"), wrapper.getScriptFile());
        assertEquals(new File(getProject().getProjectDir(), "gradlew.bat"), wrapper.getBatchScript());
        assertEquals(GradleVersion.current().getVersion(), wrapper.getGradleVersion());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, wrapper.getDistributionPath());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, wrapper.getArchivePath());
        assertEquals(Wrapper.PathBase.GRADLE_USER_HOME, wrapper.getDistributionBase());
        assertEquals(Wrapper.PathBase.GRADLE_USER_HOME, wrapper.getArchiveBase());
        assertNotNull(wrapper.getDistributionUrl());
    }

    @Test
    public void testDeterminesWindowsScriptPathFromUnixScriptPath() {
        wrapper.setScriptFile("build/gradle.sh");
        assertEquals(getProject().file("build/gradle.bat"), wrapper.getBatchScript());

        wrapper.setScriptFile("build/gradle-wrapper");
        assertEquals(getProject().file("build/gradle-wrapper.bat"), wrapper.getBatchScript());
    }

    @Test
    public void testDeterminesPropertiesFilePathFromJarPath() {
        wrapper.setJarFile("build/gradle-wrapper.jar");
        assertEquals(getProject().file("build/gradle-wrapper.properties"), wrapper.getPropertiesFile());
    }
    
    @Test
    public void testDownloadsFromReleaseRepositoryForReleaseVersions() {
        wrapper.setGradleVersion("0.9.1");
        assertEquals("http://services.gradle.org/distributions/gradle-0.9.1-bin.zip", wrapper.getDistributionUrl());
    }

    @Test
    public void testDownloadsFromReleaseRepositoryForPreviewReleaseVersions() {
        wrapper.setGradleVersion("1.0-milestone-1");
        assertEquals("http://services.gradle.org/distributions/gradle-1.0-milestone-1-bin.zip", wrapper.getDistributionUrl());
    }

    @Test
    public void testDownloadsFromSnapshotRepositoryForSnapshotVersions() {
        wrapper.setGradleVersion("0.9.1-20101224110000+1100");
        assertEquals("http://services.gradle.org/distributions-snapshots/gradle-0.9.1-20101224110000+1100-bin.zip", wrapper.getDistributionUrl());
    }

    @Test
    public void testUsesExplicitlyDefinedDistributionUrl() {
        wrapper.setGradleVersion("0.9");
        wrapper.setDistributionUrl("http://some-url");
        assertEquals("http://some-url", wrapper.getDistributionUrl());
    }

    @Test
    public void testExecuteWithNonExistingWrapperJarParentDir() throws IOException {
        checkExecute();
    }

    @Test
    public void testCheckInputs() throws IOException {
        assertThat(wrapper.getInputs().getProperties().keySet(),
                equalTo(WrapUtil.toSet("distributionBase", "distributionPath", "distributionUrl", "archiveBase", "archivePath")));
    }

    @Test
    public void testExecuteWithExistingWrapperJarParentDirAndExistingWrapperJar() throws IOException {
        File jarDir = new File(getProject().getProjectDir(), "lib");
        jarDir.mkdirs();
        File wrapperJar = new File(getProject().getProjectDir(), targetWrapperJarPath);
        File parentFile = expectedTargetWrapperJar.getParentFile();
        assertTrue(parentFile.isDirectory() || parentFile.mkdirs());
        try {
            assertTrue(expectedTargetWrapperJar.createNewFile());
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not create %s.", wrapperJar), e);
        }
        checkExecute();
    }

    private void checkExecute() throws IOException {
        wrapper.execute();
        TestFile unjarDir = tmpDir.createDir("unjar");
        expectedTargetWrapperJar.unzipTo(unjarDir);
        unjarDir.file(GradleWrapperMain.class.getName().replace(".", "/") + ".class").assertIsFile();
        Properties properties = GUtil.loadProperties(expectedTargetWrapperProperties);
        assertEquals(properties.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY), wrapper.getDistributionUrl());
        assertEquals(properties.getProperty(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY), wrapper.getDistributionBase().toString());
        assertEquals(properties.getProperty(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY), wrapper.getDistributionPath());
        assertEquals(properties.getProperty(WrapperExecutor.ZIP_STORE_BASE_PROPERTY), wrapper.getArchiveBase().toString());
        assertEquals(properties.getProperty(WrapperExecutor.ZIP_STORE_PATH_PROPERTY), wrapper.getArchivePath());
    }

    private String toNative(String s) {
        return s.replace("/", File.separator);
    }
}
