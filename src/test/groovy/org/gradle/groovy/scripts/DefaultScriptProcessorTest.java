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

package org.gradle.groovy.scripts;

import groovy.lang.Script;
import org.apache.commons.io.FileUtils;
import org.gradle.CacheUsage;
import org.gradle.api.Project;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultScriptProcessorTest {
    static final String TEST_BUILD_FILE_NAME = "mybuild.craidle";
    static final String TEST_SCRIPT_NAME = "mybuild_craidle";
    static final String TEST_SCRIPT_TEXT = "sometext";
    static final String TEST_IN_MEMORY_SCRIPT_TEXT = "someInMemoryText";
    static final String TEST_SCRIPT_ATACHEMENT = "import org.gradle.api.*";

    DefaultScriptProcessor scriptProcessor;

    File testCacheDir;
    File testScriptFileDir;
    File testScriptFile;

    ClassLoader testClassLoader;

    ScriptCompilationHandler scriptCompilationHandlerMock;

    ScriptWithSource expectedScript;

    Mockery context = new JUnit4Mockery();

    Class expectedScriptBaseClass = Script.class;

    ScriptSource source;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        scriptCompilationHandlerMock = context.mock(ScriptCompilationHandler.class);
        testClassLoader = new URLClassLoader(new URL[0]);
        testScriptFileDir = HelperUtil.makeNewTestDir("projectdir");
        testScriptFile = new File(testScriptFileDir, TEST_BUILD_FILE_NAME);
        testCacheDir = new File(new File(testScriptFileDir, Project.CACHE_DIR_NAME), TEST_BUILD_FILE_NAME);
        expectedScript = context.mock(ScriptWithSource.class);
        scriptProcessor = new DefaultScriptProcessor(scriptCompilationHandlerMock, CacheUsage.ON);
        source = context.mock(ScriptSource.class);

        context.checking(new Expectations(){{
            allowing(source).getDisplayName();
            will(returnValue("[script source]"));
            allowing(expectedScript).setSource(source);
        }});
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void testInit() {
        assertSame(scriptCompilationHandlerMock, scriptProcessor.getScriptCacheHandler());
        assertEquals(CacheUsage.ON, scriptProcessor.getCacheUsage());
    }

    @Test
    public void testWithNonExistingSourceFile() {
        context.checking(new Expectations(){{
            allowing(source).getSourceFile();
            will(returnValue(testScriptFile));

            one(scriptCompilationHandlerMock).createScriptOnTheFly(
                    source,
                    testClassLoader,
                    expectedScriptBaseClass);
            will(returnValue(expectedScript));
        }});

        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithNoSouceFile() {
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(null));

                one(scriptCompilationHandlerMock).createScriptOnTheFly(
                        source,
                        testClassLoader,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });

        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithNonCachedExistingSourceFile() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                one(scriptCompilationHandlerMock).loadFromCache(source, testClassLoader, testCacheDir, expectedScriptBaseClass);
                will(returnValue(null));

                one(scriptCompilationHandlerMock).writeToCache(
                        source,
                        testClassLoader,
                        testCacheDir,
                        expectedScriptBaseClass
                );

                one(scriptCompilationHandlerMock).loadFromCache(source, testClassLoader, testCacheDir, expectedScriptBaseClass);
                will(returnValue(expectedScript));

            }
        });
        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingCachedSourceFile() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                one(scriptCompilationHandlerMock).loadFromCache(source, testClassLoader, testCacheDir, expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });

        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingBuildFileAndRebuildCache() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                one(scriptCompilationHandlerMock).writeToCache(
                        source,
                        testClassLoader,
                        testCacheDir,
                        expectedScriptBaseClass
                );

                one(scriptCompilationHandlerMock).loadFromCache(source, testClassLoader, testCacheDir, expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });

        scriptProcessor = new DefaultScriptProcessor(scriptCompilationHandlerMock, CacheUsage.REBUILD);
        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingSourceFileAndCacheOff() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                one(scriptCompilationHandlerMock).createScriptOnTheFly(
                        source,
                        testClassLoader,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });

        scriptProcessor = new DefaultScriptProcessor(scriptCompilationHandlerMock, CacheUsage.OFF);
        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithNonExistingSourceFileAndCacheOff() {
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                one(scriptCompilationHandlerMock).createScriptOnTheFly(
                        source,
                        testClassLoader,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });

        scriptProcessor = new DefaultScriptProcessor(scriptCompilationHandlerMock, CacheUsage.OFF);
        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    private void createBuildScriptFile() {
        try {
            FileUtils.writeStringToFile(testScriptFile, TEST_SCRIPT_TEXT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}