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
import org.gradle.util.HelperUtil;
import org.junit.runner.RunWith;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.jmock.lib.legacy.ClassImposteriser;
import org.gradle.api.Project;
import org.gradle.CacheUsage;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.groovy.scripts.IProjectScriptMetaData;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.net.URLClassLoader;
import java.net.URL;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildScriptProcessorTest {
    static final String TEST_BUILD_FILE_NAME = "mybuild.craidle";
    static final String TEST_BUILD_FILE_SCRIPT_NAME = "mybuild_craidle";
    static final String TEST_SCRIPT_TEXT = "sometext";
    static final String TEST_IMPORTS = "import org.gradle.api.*";

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
    private CacheUsage expectedCacheUsage;

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
        expectedCacheUsage = CacheUsage.ON;
        buildScriptProcessor = new BuildScriptProcessor(scriptProcessorMock, projectScriptMetaDataMock,
                importsReaderMock, null, expectedCacheUsage);
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
        assertSame(expectedCacheUsage, buildScriptProcessor.getCacheUsage());
        assertNull(buildScriptProcessor.getInMemoryScriptText());
    }

    @Test
    public void testCreateScriptWithInMemoryTextNotSet() {
        context.checking(new Expectations() {
            {
                allowing(testProject).getRootDir();
                will(returnValue(testProjectDir.getParentFile()));
                allowing(testProject).getBuildScriptClassLoader();
                will(returnValue(expectedClassloader));
                allowing(testProject).getProjectDir();
                will(returnValue(testProjectDir));
                allowing(testProject).getBuildFileName();
                will(returnValue(TEST_BUILD_FILE_NAME));
                allowing(importsReaderMock).getImports(testProjectDir.getParentFile());
                will(returnValue(TEST_IMPORTS));
                one(scriptProcessorMock).createScriptFromFile(
                        new File(testProjectDir, Project.CACHE_DIR_NAME),
                        testBuildScriptFile,
                        TEST_IMPORTS,
                        CacheUsage.ON,
                        expectedClassloader,
                        ProjectScript.class);
                will(returnValue(expectedScript));
                one(projectScriptMetaDataMock).applyMetaData((ProjectScript) expectedScript, testProject);
            }
        });
        buildScriptProcessor.createScript(testProject);
    }

    @Test
    public void testCreateScriptWithInMemoryTextSet() {
        buildScriptProcessor = new BuildScriptProcessor(scriptProcessorMock, projectScriptMetaDataMock,
                importsReaderMock, TEST_SCRIPT_TEXT, expectedCacheUsage);
        context.checking(new Expectations() {
            {
                allowing(testProject).getRootDir();
                will(returnValue(testProjectDir.getParentFile()));
                allowing(testProject).getBuildScriptClassLoader();
                will(returnValue(expectedClassloader));
                allowing(testProject).getBuildFileCacheName();
                will(returnValue(TEST_BUILD_FILE_SCRIPT_NAME));
                allowing(importsReaderMock).getImports(testProjectDir.getParentFile());
                will(returnValue(TEST_IMPORTS));
                one(scriptProcessorMock).createScriptFromText(
                        TEST_SCRIPT_TEXT,
                        TEST_IMPORTS,
                        TEST_BUILD_FILE_SCRIPT_NAME,
                        expectedClassloader,
                        ProjectScript.class);
                will(returnValue(expectedScript));
                one(projectScriptMetaDataMock).applyMetaData((ProjectScript) expectedScript, testProject);
            }
        });
        buildScriptProcessor.createScript(testProject);
    }
}
