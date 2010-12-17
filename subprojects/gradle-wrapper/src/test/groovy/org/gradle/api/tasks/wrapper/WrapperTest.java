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
import org.gradle.api.tasks.wrapper.internal.WrapperScriptGenerator;
import org.gradle.util.*;
import org.gradle.wrapper.GradleWrapperMain;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class WrapperTest extends AbstractTaskTest {

    private Wrapper wrapper;
    private WrapperScriptGenerator wrapperScriptGeneratorMock;
    private String targetWrapperJarPath;
    private Mockery context = new Mockery();
    private TestFile expectedTargetWrapperJar;
    private File expectedTargetWrapperProperties;
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        wrapper = createTask(Wrapper.class);
        wrapperScriptGeneratorMock = context.mock(WrapperScriptGenerator.class);
        wrapper.setScriptDestinationPath("scriptDestination");
        wrapper.setGradleVersion("1.0");
        targetWrapperJarPath = "jarPath";
        expectedTargetWrapperJar = new TestFile(getProject().getProjectDir(),
                targetWrapperJarPath + "/" + Wrapper.WRAPPER_JAR);
        expectedTargetWrapperProperties = new File(getProject().getProjectDir(),
                targetWrapperJarPath + "/" + Wrapper.WRAPPER_PROPERTIES);
        new File(getProject().getProjectDir(), targetWrapperJarPath).mkdirs();
        wrapper.setJarPath(targetWrapperJarPath);
        wrapper.setDistributionPath("somepath");
        wrapper.setUnixWrapperScriptGenerator(wrapperScriptGeneratorMock);
    }

    public AbstractTask getTask() {
        return wrapper;
    }

    @Test
    public void testWrapperDefaults() {
        wrapper = createTask(Wrapper.class);
        assertEquals(new File(getProject().getProjectDir(), "gradle/wrapper/gradle-wrapper.jar"), wrapper.getJarFile());
        assertEquals("gradle/wrapper", wrapper.getJarPath());
        assertEquals(new File(getProject().getProjectDir(), "gradlew"), wrapper.getScriptFile());
        assertEquals(".", wrapper.getScriptDestinationPath());
        assertEquals(new GradleVersion().getVersion(), wrapper.getGradleVersion());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, wrapper.getDistributionPath());
        assertEquals(Wrapper.DEFAULT_ARCHIVE_NAME, wrapper.getArchiveName());
        assertEquals(Wrapper.DEFAULT_ARCHIVE_CLASSIFIER, wrapper.getArchiveClassifier());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, wrapper.getArchivePath());
        assertEquals(Wrapper.DEFAULT_URL_ROOT, wrapper.getUrlRoot());
        assertEquals(Wrapper.PathBase.GRADLE_USER_HOME, wrapper.getDistributionBase());
        assertEquals(Wrapper.PathBase.GRADLE_USER_HOME, wrapper.getArchiveBase());
    }

    @Test
    public void testExecuteWithNonExistingWrapperJarParentDir() throws IOException {
        checkExecute();
    }

    @Test
    public void testCheckInputs() throws IOException {
        assertThat(wrapper.getInputs().getProperties().keySet(),
                equalTo(WrapUtil.toSet("archiveClassifier", "distributionPath", "archiveName", "urlRoot", "gradleVersion", "archivePath")));
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
        context.checking(new Expectations() {
            {
                one(wrapperScriptGeneratorMock).generate(
                        toNative("../" + targetWrapperJarPath + "/" + Wrapper.WRAPPER_JAR),
                        toNative("../" + targetWrapperJarPath + "/" + Wrapper.WRAPPER_PROPERTIES),
                        wrapper.getScriptFile());
            }
        });
        wrapper.execute();
        TestFile unjarDir = tmpDir.createDir("unjar");
        expectedTargetWrapperJar.unzipTo(unjarDir);
        unjarDir.file(GradleWrapperMain.class.getName().replace(".", "/") + ".class").assertIsFile();
        Properties properties = GUtil.loadProperties(expectedTargetWrapperProperties);
        assertEquals(properties.getProperty(Wrapper.URL_ROOT_PROPERTY), wrapper.getUrlRoot());
        assertEquals(properties.getProperty(Wrapper.DISTRIBUTION_BASE_PROPERTY), wrapper.getDistributionBase().toString());
        assertEquals(properties.getProperty(Wrapper.DISTRIBUTION_PATH_PROPERTY), wrapper.getDistributionPath());
        assertEquals(properties.getProperty(Wrapper.DISTRIBUTION_NAME_PROPERTY), wrapper.getArchiveName());
        assertEquals(properties.getProperty(Wrapper.DISTRIBUTION_CLASSIFIER_PROPERTY), wrapper.getArchiveClassifier());
        assertEquals(properties.getProperty(Wrapper.DISTRIBUTION_VERSION_PROPERTY), wrapper.getGradleVersion());
        assertEquals(properties.getProperty(Wrapper.ZIP_STORE_BASE_PROPERTY), wrapper.getArchiveBase().toString());
        assertEquals(properties.getProperty(Wrapper.ZIP_STORE_PATH_PROPERTY), wrapper.getArchivePath());
    }

    private String toNative(String s) {
        return s.replace("/", File.separator);
    }
}