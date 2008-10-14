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
import org.gradle.invocation.DefaultBuild;
import org.gradle.util.AntUtil;
import org.gradle.util.CompressUtil;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestConsts;
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
    private String expectedTargetWrapperJar;

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        wrapper = new Wrapper(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        wrapperScriptGeneratorMock = context.mock(WrapperScriptGenerator.class);
        wrapper.setScriptDestinationPath("scriptDestination");
        wrapper.setGradleVersion("1.0");
        testDir = HelperUtil.makeNewTestDir();
        File testGradleHome = new File(testDir, "testGradleHome");
        File testGradleHomeLib = new File(testGradleHome, "lib");
        testGradleHomeLib.mkdirs();
        createSourceWrapperJar(testGradleHomeLib);
        ((DefaultBuild) getProject().getBuild()).getStartParameter().setGradleHomeDir(testGradleHome);
        targetWrapperJarPath = "jarPath";
        expectedTargetWrapperJar = targetWrapperJarPath + "/" + Install.WRAPPER_JAR;
        new File(getProject().getProjectDir(), targetWrapperJarPath).mkdirs();
        distributionPath = "somepath";
        wrapper.setJarPath(targetWrapperJarPath);
        wrapper.setDistributionPath(distributionPath);
        wrapper.setUnixWrapperScriptGenerator(wrapperScriptGeneratorMock);
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
        new File(getProject().getProjectDir(), targetWrapperJarPath).createNewFile();
        checkExecute();
    }

    private void checkExecute() throws IOException {
        context.checking(new Expectations() {
            {
                one(wrapperScriptGeneratorMock).generate(
                        targetWrapperJarPath + "/" + Install.WRAPPER_JAR,
                        new File(getProject().getProjectDir(), wrapper.getScriptDestinationPath()));
            }
        });
        wrapper.execute();
        File unjarDir = HelperUtil.makeNewTestDir("unjar");
        CompressUtil.unzip(new File(getProject().getProjectDir(), expectedTargetWrapperJar), unjarDir);
        assertEquals(TEST_TEXT, FileUtils.readFileToString(new File(unjarDir, TEST_FILE_NAME)));
        Properties properties = GUtil.createProperties(new File(unjarDir.getAbsolutePath() + "/org/gradle/wrapper/wrapper.properties"));

    }


}