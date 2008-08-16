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

package org.gradle.api.internal.project;

import groovy.lang.Script;
import org.gradle.groovy.scripts.IProjectScriptMetaData;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.util.HelperUtil;
import org.gradle.util.ReflectionEqualsMatcher;
import static org.gradle.util.ReflectionEqualsMatcher.*;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildScriptProcessorTest {
    static final String TEST_BUILD_FILE_NAME = "mybuild.craidle";
    static final String TEST_SCRIPT_TEXT = "sometext";

    BuildScriptProcessor buildScriptProcessor;

    DefaultProject testProject;

    File testProjectDir;
    File testBuildScriptFile;

    ImportsReader importsReaderMock;

    Script expectedScript;

    ClassLoader expectedClassloader;

    IScriptProcessor scriptProcessorMock;

    IProjectScriptMetaData projectScriptMetaDataMock;

    Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        testProjectDir = HelperUtil.makeNewTestDir("projectdir");
        testBuildScriptFile = new File(testProjectDir, TEST_BUILD_FILE_NAME);
        testProject = context.mock(DefaultProject.class);
        importsReaderMock = context.mock(ImportsReader.class);
        scriptProcessorMock = context.mock(IScriptProcessor.class);
        expectedClassloader = new URLClassLoader(new URL[0]);
        projectScriptMetaDataMock = context.mock(IProjectScriptMetaData.class);
        expectedScript = new ProjectScript() {
            public Object run() {
                return null; 
            }
        };
        buildScriptProcessor = new BuildScriptProcessor(scriptProcessorMock, projectScriptMetaDataMock,
                importsReaderMock, null);
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void testInit() {
        assertSame(scriptProcessorMock, buildScriptProcessor.getScriptProcessor());
        assertSame(projectScriptMetaDataMock, buildScriptProcessor.getProjectScriptMetaData());
        assertSame(importsReaderMock, buildScriptProcessor.getImportsReader());
        assertNull(buildScriptProcessor.getInMemoryScriptText());
    }

    @Test
    public void testCreateScriptWithInMemoryTextNotSet() {
        final ScriptSource expectedScriptSource = new FileScriptSource("build file", testBuildScriptFile, importsReaderMock);

        context.checking(new Expectations() {
            {
                allowing(testProject).getBuildScriptClassLoader();
                will(returnValue(expectedClassloader));
                allowing(testProject).getProjectDir();
                will(returnValue(testProjectDir));
                allowing(testProject).getBuildFileName();
                will(returnValue(TEST_BUILD_FILE_NAME));
                one(scriptProcessorMock).createScript(
                        with(reflectionEquals(expectedScriptSource)),
                        with(same(expectedClassloader)),
                        with(equal(ProjectScript.class)));
                will(returnValue(expectedScript));
                one(projectScriptMetaDataMock).applyMetaData(expectedScript, testProject);
            }
        });
        buildScriptProcessor.createScript(testProject);
    }

    @Test
    public void testCreateScriptWithInMemoryTextSet() {
        buildScriptProcessor = new BuildScriptProcessor(scriptProcessorMock, projectScriptMetaDataMock,
                importsReaderMock, TEST_SCRIPT_TEXT);
        final File rootDir = testProjectDir.getParentFile();
        final ScriptSource expectedScriptSource = new StringScriptSource("embedded build script", TEST_SCRIPT_TEXT, rootDir, importsReaderMock);

        context.checking(new Expectations() {
            {
                allowing(testProject).getRootDir();
                will(returnValue(rootDir));
                allowing(testProject).getBuildScriptClassLoader();
                will(returnValue(expectedClassloader));
                one(scriptProcessorMock).createScript(
                        with(reflectionEquals(expectedScriptSource)),
                        with(same(expectedClassloader)),
                        with(equal(ProjectScript.class)));
                will(returnValue(expectedScript));
                one(projectScriptMetaDataMock).applyMetaData(expectedScript, testProject);
            }
        });
        buildScriptProcessor.createScript(testProject);
    }
}
