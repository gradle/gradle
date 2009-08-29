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

import org.gradle.CacheUsage;
import org.gradle.api.Project;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
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
@RunWith(JMock.class)
public class DefaultScriptProcessorFactoryTest {
    static final String TEST_BUILD_FILE_NAME = "mybuild.craidle";
    static final String TEST_SCRIPT_TEXT = "sometext";

    DefaultScriptProcessorFactory scriptProcessor;

    File testCacheDir;
    File testScriptFileDir;
    File testScriptFile;

    ClassLoader testClassLoader;
    ClassLoader originalClassLoader;

    ScriptCompilationHandler scriptCompilationHandlerMock;
    ScriptRunnerFactory scriptRunnerFactoryMock;

    Script expectedScript;

    Mockery context = new JUnit4Mockery();

    Class expectedScriptBaseClass = groovy.lang.Script.class;

    ScriptSource source;
    private ScriptRunner expectedScriptRunner;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        scriptCompilationHandlerMock = context.mock(ScriptCompilationHandler.class);
        scriptRunnerFactoryMock = context.mock(ScriptRunnerFactory.class);
        testClassLoader = new URLClassLoader(new URL[0]);
        testScriptFileDir = HelperUtil.makeNewTestDir("projectdir");
        testScriptFile = new File(testScriptFileDir, TEST_BUILD_FILE_NAME);
        testCacheDir = new File(new File(new File(testScriptFileDir, Project.CACHE_DIR_NAME), TEST_BUILD_FILE_NAME), "NoTransformer");
        expectedScript = context.mock(Script.class);
        expectedScriptRunner = context.mock(ScriptRunner.class);
        scriptProcessor = new DefaultScriptProcessorFactory(scriptCompilationHandlerMock, CacheUsage.ON, scriptRunnerFactoryMock);
        source = context.mock(ScriptSource.class);

        context.checking(new Expectations() {{
            allowing(source).getDisplayName();
            will(returnValue("[script source]"));
        }});

        originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(testClassLoader);
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    @Test
    public void testWithNonExistingSourceFile() {
        context.checking(new Expectations(){{
            allowing(source).getSourceFile();
            will(returnValue(testScriptFile));

            one(scriptCompilationHandlerMock).createScriptOnTheFly(
                    source,
                    testClassLoader,
                    null,
                    expectedScriptBaseClass);
            will(returnValue(expectedScript));

            one(expectedScript).setScriptSource(source);

            one(scriptRunnerFactoryMock).create(expectedScript);
            will(returnValue(expectedScriptRunner));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).compile(expectedScriptBaseClass));
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
                        null,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));

                one(expectedScript).setScriptSource(source);

                one(scriptRunnerFactoryMock).create(expectedScript);
                will(returnValue(expectedScriptRunner));
            }
        });

        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).compile(expectedScriptBaseClass));
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
                        null,
                        expectedScriptBaseClass);

                one(scriptCompilationHandlerMock).loadFromCache(source, testClassLoader, testCacheDir, expectedScriptBaseClass);
                will(returnValue(expectedScript));

                one(expectedScript).setScriptSource(source);

                one(scriptRunnerFactoryMock).create(expectedScript);
                will(returnValue(expectedScriptRunner));
            }
        });

        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).compile(expectedScriptBaseClass));
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

                one(expectedScript).setScriptSource(source);

                one(scriptRunnerFactoryMock).create(expectedScript);
                will(returnValue(expectedScriptRunner));
            }
        });

        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).compile(expectedScriptBaseClass));
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
                        null,
                        expectedScriptBaseClass);

                one(scriptCompilationHandlerMock).loadFromCache(source, testClassLoader, testCacheDir, expectedScriptBaseClass);
                will(returnValue(expectedScript));

                one(expectedScript).setScriptSource(source);

                one(scriptRunnerFactoryMock).create(expectedScript);
                will(returnValue(expectedScriptRunner));
            }
        });

        scriptProcessor = new DefaultScriptProcessorFactory(scriptCompilationHandlerMock, CacheUsage.REBUILD, scriptRunnerFactoryMock);
        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).compile(expectedScriptBaseClass));
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
                        null,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));

                one(expectedScript).setScriptSource(source);

                one(scriptRunnerFactoryMock).create(expectedScript);
                will(returnValue(expectedScriptRunner));
            }
        });

        scriptProcessor = new DefaultScriptProcessorFactory(scriptCompilationHandlerMock, CacheUsage.OFF, scriptRunnerFactoryMock);
        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).compile(expectedScriptBaseClass));
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
                        null,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));

                one(expectedScript).setScriptSource(source);

                one(scriptRunnerFactoryMock).create(expectedScript);
                will(returnValue(expectedScriptRunner));
            }
        });

        scriptProcessor = new DefaultScriptProcessorFactory(scriptCompilationHandlerMock, CacheUsage.OFF, scriptRunnerFactoryMock);
        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).compile(expectedScriptBaseClass));
    }

    @Test
    public void testUsesSuppliedClassLoader() {
        final ClassLoader classLoader = new ClassLoader() {
        };

        context.checking(new Expectations(){{
            allowing(source).getSourceFile();
            will(returnValue(testScriptFile));

            one(scriptCompilationHandlerMock).createScriptOnTheFly(
                    source,
                    classLoader,
                    null,
                    expectedScriptBaseClass);
            will(returnValue(expectedScript));

            one(expectedScript).setScriptSource(source);

            one(scriptRunnerFactoryMock).create(expectedScript);
            will(returnValue(expectedScriptRunner));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).setClassloader(classLoader).compile(expectedScriptBaseClass));
    }

    @Test
    public void testUsesSuppliedTransformerToGenerateCacheDir() {
        createBuildScriptFile();

        final Transformer transformer = context.mock(Transformer.class);
        final File expectedCacheDir = new File(testCacheDir.getParentFile(), transformer.getClass().getSimpleName());

        context.checking(new Expectations(){{
            allowing(source).getSourceFile();
            will(returnValue(testScriptFile));

            one(scriptCompilationHandlerMock).loadFromCache(
                    source,
                    testClassLoader,
                    expectedCacheDir,
                    expectedScriptBaseClass);
            will(returnValue(expectedScript));

            one(expectedScript).setScriptSource(source);

            one(scriptRunnerFactoryMock).create(expectedScript);
            will(returnValue(expectedScriptRunner));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createProcessor(source).setTransformer(transformer).compile(expectedScriptBaseClass));
    }
    
    private void createBuildScriptFile() {
        GFileUtils.writeStringToFile(testScriptFile, TEST_SCRIPT_TEXT);
    }
}