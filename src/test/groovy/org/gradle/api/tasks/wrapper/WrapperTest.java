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

import org.gradle.api.Task;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.*;
import org.gradle.Main;
import org.gradle.wrapper.Install;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.GUnzip;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

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

    private String originalGradleHome;
    private Wrapper wrapper;
    private UnixWrapperScriptGenerator unixWrapperScriptGeneratorMock;
    private WindowsExeGenerator windowsExeGenerator;
    private File testDir;
    private File sourceWrapperJar;
    private String distributionPath;
    private String zipPath;
    private String targetWrapperJarPath;
    private Mockery context = new Mockery();
    private String expectedTargetWrapperJar;
    private Wrapper.PathBase expectedDistributionBase;
    private Wrapper.PathBase expectedZipBase;

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        wrapper = new Wrapper(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        unixWrapperScriptGeneratorMock = context.mock(UnixWrapperScriptGenerator.class);
        windowsExeGenerator = context.mock(WindowsExeGenerator.class);
        wrapper.setScriptDestinationPath("scriptDestination");
        wrapper.setGradleVersion("1.0");
        testDir = HelperUtil.makeNewTestDir();
        File testGradleHome = new File(testDir, "testGradleHome");
        File testGradleHomeLib = new File(testGradleHome, "lib");
        testGradleHomeLib.mkdirs();
        createSourceWrapperJar(testGradleHomeLib);
        originalGradleHome = System.getProperty(Main.GRADLE_HOME_PROPERTY_KEY);
        System.setProperty(Main.GRADLE_HOME_PROPERTY_KEY, testGradleHome.getAbsolutePath());
        targetWrapperJarPath = "jarPath";
        expectedTargetWrapperJar = targetWrapperJarPath + "/" + Install.WRAPPER_JAR;
        new File(getProject().getProjectDir(), targetWrapperJarPath).mkdirs();
        distributionPath = "somepath";
        zipPath = "myzippath";
        wrapper.setJarPath(targetWrapperJarPath);
        wrapper.setDistributionPath(distributionPath);
        wrapper.setUnixWrapperScriptGenerator(unixWrapperScriptGeneratorMock);
        wrapper.setWindowsExeGenerator(windowsExeGenerator);
        expectedDistributionBase = Wrapper.PathBase.PROJECT;
        expectedZipBase = Wrapper.PathBase.PROJECT;
    }

    private void createSourceWrapperJar(File testGradleHomeLib) {
        File sourceWrapperExplodedDir = new File(testGradleHomeLib, Wrapper.WRAPPER_JAR_BASE_NAME + "-" + TestConsts.VERSION);
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
        if (originalGradleHome != null) {
            System.setProperty(Main.GRADLE_HOME_PROPERTY_KEY, originalGradleHome);
        }
    }

    public Task getTask() {
        return wrapper;
    }

    @Test
    public void testWrapper() {
        wrapper = new Wrapper(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        assertEquals("", wrapper.getJarPath());
        assertEquals("", wrapper.getScriptDestinationPath());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, wrapper.getDistributionPath());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_NAME, wrapper.getDistributionName());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_CLASSIFIER, wrapper.getDistributionClassifier());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, wrapper.getZipPath());
        assertEquals(Wrapper.DEFAULT_URL_ROOT, wrapper.getUrlRoot());
        assertEquals(Wrapper.PathBase.GRADLE_USER_HOME, wrapper.getDistributionBase());
        assertEquals(Wrapper.PathBase.GRADLE_USER_HOME, wrapper.getZipBase());
    }

    @Test
    public void testExecuteWithNonExistingWrapperJarParentDir() throws IOException {
        checkExecute();
    }

    @Test
    public void testExecuteWithExistingWrapperJarParentDirAndExistingWrapperJar() throws IOException {
        File jarDir = new File(getProject().getProjectDir(), "lib");
        jarDir.mkdirs();
        new File(getProject().getProjectDir(), targetWrapperJarPath).createNewFile();
        checkExecute();
    }

    private void checkExecute() throws IOException {
        context.checking(new Expectations() {
            {
                one(unixWrapperScriptGeneratorMock).generate(
                        targetWrapperJarPath + "/" + Install.WRAPPER_JAR,
                        new File(getProject().getProjectDir(), wrapper.getScriptDestinationPath()));
                one(windowsExeGenerator).generate(
                        targetWrapperJarPath + "/" + Install.WRAPPER_JAR,
                        new File(getProject().getProjectDir(), wrapper.getScriptDestinationPath()),
                        getProject().getBuildDir(),
                        getProject().getAnt());
            }
        });
        wrapper.execute();
        File unjarDir = HelperUtil.makeNewTestDir("unjar");
        CompressUtil.unzip(new File(getProject().getProjectDir(), expectedTargetWrapperJar), unjarDir);
        assertEquals(TEST_TEXT, FileUtils.readFileToString(new File(unjarDir, TEST_FILE_NAME)));
        Properties properties = GUtil.createProperties(new File(unjarDir.getAbsolutePath() + "/org/gradle/wrapper/wrapper.properties"));

    }


}