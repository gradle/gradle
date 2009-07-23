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

package org.gradle.api.tasks.wrapper;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.taskdefs.Jar;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.*;
import org.gradle.wrapper.Install;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class WrapperTest extends AbstractTaskTest {
    public static final String TEST_TEXT = "sometext";
    public static final String TEST_FILE_NAME = "somefile";

    private Wrapper wrapper;
    private WrapperScriptGenerator wrapperScriptGeneratorMock;
    private File testDir;
    private File sourceWrapperJar;
    private String distributionPath;
    private String targetWrapperJarPath;
    private Mockery context = new Mockery();
    private File expectedTargetWrapperJar;
    private File expectedTargetWrapperProperties;

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        wrapper = createTask(Wrapper.class);
        wrapperScriptGeneratorMock = context.mock(WrapperScriptGenerator.class);
        wrapper.setScriptDestinationPath("scriptDestination");
        wrapper.setGradleVersion("1.0");
        testDir = HelperUtil.makeNewTestDir();
        File testGradleHome = new File(testDir, "testGradleHome");
        File testGradleHomeLib = new File(testGradleHome, "lib");
        testGradleHomeLib.mkdirs();
        createSourceWrapperJar(testGradleHomeLib);
        getProject().getBuild().getStartParameter().setGradleHomeDir(testGradleHome);
        targetWrapperJarPath = "jarPath";
        expectedTargetWrapperJar = new File(getProject().getProjectDir(),
                targetWrapperJarPath + "/" + Install.WRAPPER_JAR);
        expectedTargetWrapperProperties = new File(getProject().getProjectDir(),
                targetWrapperJarPath + "/" + Install.WRAPPER_PROPERTIES);
        new File(getProject().getProjectDir(), targetWrapperJarPath).mkdirs();
        distributionPath = "somepath";
        wrapper.setJarPath(targetWrapperJarPath);
        wrapper.setDistributionPath(distributionPath);
        wrapper.setUnixWrapperScriptGenerator(wrapperScriptGeneratorMock);
    }

    private void createSourceWrapperJar(File testGradleHomeLib) {
        File sourceWrapperExplodedDir = new File(testGradleHomeLib, Wrapper.WRAPPER_JAR_BASE_NAME + "-" + getProject().getBuild().getGradleVersion());
        sourceWrapperExplodedDir.mkdirs();
        GFileUtils.writeStringToFile(new File(sourceWrapperExplodedDir, TEST_FILE_NAME), TEST_TEXT);
        sourceWrapperJar = new File(testGradleHomeLib, sourceWrapperExplodedDir.getName() + ".jar");
        Jar jarTask = new Jar();
        jarTask.setBasedir(sourceWrapperExplodedDir);
        jarTask.setDestFile(sourceWrapperJar);
        AntUtil.execute(jarTask);
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    public AbstractTask getTask() {
        return wrapper;
    }

    @Test
    public void testWrapper() {
        wrapper = new Wrapper(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        assertEquals("", wrapper.getJarPath());
        assertEquals("", wrapper.getScriptDestinationPath());
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
                        targetWrapperJarPath + "/" + Install.WRAPPER_JAR,
                        targetWrapperJarPath + "/" + Install.WRAPPER_PROPERTIES,
                        new File(getProject().getProjectDir(), wrapper.getScriptDestinationPath()));
            }
        });
        wrapper.execute();
        File unjarDir = HelperUtil.makeNewTestDir("unjar");
        CompressUtil.unzip(expectedTargetWrapperJar, unjarDir);
        assertEquals(TEST_TEXT, FileUtils.readFileToString(new File(unjarDir, TEST_FILE_NAME)));
        Properties properties = GUtil.loadProperties(expectedTargetWrapperProperties);
        assertEquals(properties.getProperty(org.gradle.wrapper.Wrapper.URL_ROOT_PROPERTY), wrapper.getUrlRoot());
        assertEquals(properties.getProperty(org.gradle.wrapper.Wrapper.DISTRIBUTION_BASE_PROPERTY), wrapper.getDistributionBase().toString());
        assertEquals(properties.getProperty(org.gradle.wrapper.Wrapper.DISTRIBUTION_PATH_PROPERTY), wrapper.getDistributionPath());
        assertEquals(properties.getProperty(org.gradle.wrapper.Wrapper.DISTRIBUTION_NAME_PROPERTY), wrapper.getArchiveName());
        assertEquals(properties.getProperty(org.gradle.wrapper.Wrapper.DISTRIBUTION_CLASSIFIER_PROPERTY), wrapper.getArchiveClassifier());
        assertEquals(properties.getProperty(org.gradle.wrapper.Wrapper.DISTRIBUTION_VERSION_PROPERTY), wrapper.getGradleVersion());
        assertEquals(properties.getProperty(org.gradle.wrapper.Wrapper.ZIP_STORE_BASE_PROPERTY), wrapper.getArchiveBase().toString());
        assertEquals(properties.getProperty(org.gradle.wrapper.Wrapper.ZIP_STORE_PATH_PROPERTY), wrapper.getArchivePath());
    }
}