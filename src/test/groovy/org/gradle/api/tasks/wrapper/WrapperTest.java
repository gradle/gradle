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
import org.gradle.util.HelperUtil;
import org.gradle.util.TestConsts;
import org.gradle.Main;
import org.gradle.wrapper.Install;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class WrapperTest extends AbstractTaskTest {
    private String originalGradleHome;
    private Wrapper wrapper;
    private WrapperScriptGenerator wrapperScriptGeneratorMock;
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
    public void setUp()  {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        wrapper = new Wrapper(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        wrapperScriptGeneratorMock = context.mock(WrapperScriptGenerator.class);
        wrapper.setScriptDestinationPath("scriptDestination");
        wrapper.setGradleVersion("1.0");
        testDir = HelperUtil.makeNewTestDir();
        File gradleHome = new File(testDir, "gradleHome");
        File gradleHomeLib = new File(gradleHome, "lib");
        gradleHomeLib.mkdirs();
        originalGradleHome = System.getProperty(Main.GRADLE_HOME_PROPERTY_KEY);
        System.setProperty(Main.GRADLE_HOME_PROPERTY_KEY, gradleHome.getAbsolutePath());

        sourceWrapperJar = new File(gradleHomeLib.getAbsolutePath(),
                Wrapper.WRAPPER_JAR_BASE_NAME + "-" + TestConsts.VERSION + ".jar");
        try {
            FileUtils.writeStringToFile(sourceWrapperJar, "sometext" + System.currentTimeMillis());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        targetWrapperJarPath = "lib";
        expectedTargetWrapperJar = "lib/" + Install.WRAPPER_JAR;
        distributionPath = "somepath";
        zipPath = "myzippath";
        wrapper.setJarPath(targetWrapperJarPath);
        wrapper.setDistributionPath(distributionPath);
        expectedDistributionBase = Wrapper.PathBase.PROJECT;
        expectedZipBase = Wrapper.PathBase.PROJECT;
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

    @Test public void testWrapper() {
        wrapper = new Wrapper(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        assertEquals("", wrapper.getJarPath());
        assertEquals("", wrapper.getScriptDestinationPath());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, wrapper.getDistributionPath());
        assertEquals(Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME, wrapper.getZipPath());
        assertEquals(Wrapper.DEFAULT_URL_ROOT, wrapper.getUrlRoot());
        assertEquals(Wrapper.PathBase.GRADLE_USER_HOME, wrapper.getDistributionBase());
        assertEquals(Wrapper.PathBase.GRADLE_USER_HOME, wrapper.getZipBase());
    }

    @Test public void testExecuteWithNonExistingWrapperJarParentDir() throws IOException {
        checkExecute();
    }

    @Test public void testExecuteWithExistingWrapperJarParentDirAndExistingWrapperJar() throws IOException {
        File jarDir = new File(getProject().getProjectDir(), "lib");
        jarDir.mkdirs();
        new File(getProject().getProjectDir(), targetWrapperJarPath).createNewFile();
        checkExecute();
    }

    private void checkExecute() throws IOException {
        context.checking(new Expectations() {
            {
                one(wrapperScriptGeneratorMock).generate(
                        wrapper.getGradleVersion(),
                        wrapper.getUrlRoot(),
                        targetWrapperJarPath + "/" + Install.WRAPPER_JAR,
                        expectedDistributionBase,
                        distributionPath,
                        new File(getProject().getProjectDir(), wrapper.getScriptDestinationPath()),
                        expectedZipBase,
                        zipPath);
            }
        });
        wrapper.execute();
        assertEquals(FileUtils.readFileToString(sourceWrapperJar),
                FileUtils.readFileToString(new File(getProject().getProjectDir(), expectedTargetWrapperJar)));
    }
}