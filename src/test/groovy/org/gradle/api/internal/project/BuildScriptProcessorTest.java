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

import groovy.mock.interceptor.MockFor;
import groovy.lang.Script;
import org.gradle.util.HelperUtil;
import org.junit.runner.RunWith;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.api.Expectation;
import org.gradle.util.JUnit4GroovyMockery;
import org.junit.Before;
import org.jmock.lib.legacy.ClassImposteriser;
import org.gradle.api.Project;
import org.junit.After;
import org.junit.Test;
import org.junit.Assert;
import static org.junit.Assert.assertSame;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildScriptProcessorTest {
    static final String TEST_BUILD_FILE_NAME = "mybuild.craidle";
    static final String TEST_SCRIPT_TEXT = "sometext";
    static final String TEST_IMPORTS = "import org.gradle.api.*";

    BuildScriptProcessor buildScriptProcessor;

    Project testProject;

    File testProjectDir;
    File testBuildScriptFile;

    ImportsReader importsReaderMock;

    ScriptHandler scriptHandlerMock;

    Script expectedScript;

    Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        testProjectDir = HelperUtil.makeNewTestDir("projectdir");
        testBuildScriptFile = new File(testProjectDir, TEST_BUILD_FILE_NAME);
        testProject = context.mock(Project.class);
        importsReaderMock = context.mock(ImportsReader.class);

        context.checking(new Expectations() {
            {
                allowing(testProject).getRootDir();
                will(returnValue(testProjectDir.getParentFile()));
                allowing(testProject).getProjectDir();
                will(returnValue(testProjectDir));
                allowing(testProject).getBuildFileName();
                will(returnValue(TEST_BUILD_FILE_NAME));

                allowing(importsReaderMock).getImports(testProjectDir.getParentFile());
                will(returnValue(TEST_IMPORTS));
            }
        });
        buildScriptProcessor = new BuildScriptProcessor(importsReaderMock, null, true);
        buildScriptProcessor.scriptHandler = scriptHandlerMock = context.mock(ScriptHandler.class);
        expectedScript = HelperUtil.createTestScript();
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void testInit() {
        assert buildScriptProcessor.importsReader == importsReaderMock;
        assert buildScriptProcessor.inMemoryScriptText == null;
    }

    @Test
    public void testWithNonExistingBuildFile() {
        assert buildScriptProcessor.createScript(testProject) instanceof EmptyScript;
    }

    @Test
    public void testWithNonCachedExistingBuildScript() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).loadFromCache(with(same(testProject)), with(equal(testBuildScriptFile.lastModified())));
                will(returnValue(null));
                one(scriptHandlerMock).writeToCache(with(same(testProject)),
                        with(equal(TEST_SCRIPT_TEXT + System.getProperty("line.separator") + TEST_IMPORTS)));
                will(returnValue(expectedScript));
            }
        });

        assertSame(expectedScript, buildScriptProcessor.createScript(testProject));
    }

    @Test
    public void testWithCachedBuildScript() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).loadFromCache(with(same(testProject)), with(equal(testBuildScriptFile.lastModified())));
                will(returnValue(expectedScript));
                never(scriptHandlerMock).writeToCache(with(any(testProject.getClass())), with(any(String.class)));
            }
        });

        assertSame(expectedScript, buildScriptProcessor.createScript(testProject));
    }

    @Test
    public void testWithInMemoryScriptText() {
        buildScriptProcessor = new BuildScriptProcessor(importsReaderMock, TEST_SCRIPT_TEXT, true);
        buildScriptProcessor.scriptHandler = scriptHandlerMock;
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).createScript(with(same(testProject)),
                        with(equal(TEST_SCRIPT_TEXT + System.getProperty("line.separator") + TEST_IMPORTS)));
                will(returnValue(expectedScript));
            }
        });
        assertSame(expectedScript, buildScriptProcessor.createScript(testProject));
    }

    @Test
    public void testWithNoCache() {
        createBuildScriptFile();
        buildScriptProcessor = new BuildScriptProcessor(importsReaderMock, null, false);
        buildScriptProcessor.scriptHandler = scriptHandlerMock;
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).createScript(with(same(testProject)),
                        with(equal(TEST_SCRIPT_TEXT + System.getProperty("line.separator") + TEST_IMPORTS)));
                will(returnValue(expectedScript));
            }
        });
        assertSame(expectedScript, buildScriptProcessor.createScript(testProject));
    }

    @Test
    public void testWithNoCacheAndNoScript() {
        buildScriptProcessor = new BuildScriptProcessor(importsReaderMock, null, false);
        assert buildScriptProcessor.createScript(testProject) instanceof EmptyScript;
    }

    private void createBuildScriptFile() {
        try {
            FileUtils.writeStringToFile(testBuildScriptFile, TEST_SCRIPT_TEXT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
