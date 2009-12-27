/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.integtests.TestFile;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.util.GUtil;
import org.gradle.util.HashUtil;
import org.gradle.util.TemporaryFolder;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultScriptCompilerFactoryTest {
    static final String TEST_SCRIPT_TEXT = "sometext";

    DefaultScriptCompilerFactory scriptProcessor;

    File cacheDir;
    File expectedScriptCacheDir;
    File testScriptFile;

    ClassLoader testClassLoader;
    ClassLoader originalClassLoader;

    ScriptCompilationHandler scriptCompilationHandlerMock;
    ScriptRunnerFactory scriptRunnerFactoryMock;
    CacheRepository cacheRepositoryMock;
    PersistentCache cacheMock;

    Script expectedScript;

    Mockery context = new JUnit4Mockery();

    Class expectedScriptBaseClass = groovy.lang.Script.class;
    Map<String, Object> expectedCacheProperties;

    ScriptSource source;
    private ScriptRunner expectedScriptRunner;
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        scriptCompilationHandlerMock = context.mock(ScriptCompilationHandler.class);
        scriptRunnerFactoryMock = context.mock(ScriptRunnerFactory.class);
        cacheRepositoryMock = context.mock(CacheRepository.class);
        cacheMock = context.mock(PersistentCache.class);
        testClassLoader = new URLClassLoader(new URL[0]);
        testScriptFile = new File(tmpDir.getDir(), "script/mybuild.craidle");
        cacheDir = new File(tmpDir.getDir(), "cache");
        expectedScriptCacheDir = new TestFile(cacheDir, "Script").createDir();
        expectedScript = context.mock(Script.class);
        expectedScriptRunner = context.mock(ScriptRunner.class);
        scriptProcessor = new DefaultScriptCompilerFactory(scriptCompilationHandlerMock, scriptRunnerFactoryMock, cacheRepositoryMock);
        source = context.mock(ScriptSource.class);
        String expectedHash = HashUtil.createHash(TEST_SCRIPT_TEXT);
        expectedCacheProperties = GUtil.map("source.filename", "file-name", "source.hash", expectedHash);

        context.checking(new Expectations() {{
            allowing(source).getDisplayName();
            will(returnValue("[script source]"));
            allowing(source).getClassName();
            will(returnValue("class-name"));
            allowing(source).getFileName();
            will(returnValue("file-name"));
            allowing(source).getText();
            will(returnValue(TEST_SCRIPT_TEXT));

            allowing(cacheMock).getBaseDir();
            will(returnValue(cacheDir));
        }});

        originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(testClassLoader);
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    @Test
    public void testWithSourceFileNotCached() {
        context.checking(new Expectations() {{
            one(cacheRepositoryMock).getGlobalCache("scripts/class-name", expectedCacheProperties);
            will(returnValue(cacheMock));

            allowing(cacheMock).isValid();
            will(returnValue(false));

            one(scriptCompilationHandlerMock).compileScriptToDir(source, testClassLoader, expectedScriptCacheDir, null,
                    expectedScriptBaseClass);

            one(cacheMock).markValid();

            one(scriptCompilationHandlerMock).loadScriptFromDir(source, testClassLoader, expectedScriptCacheDir,
                    expectedScriptBaseClass);
            will(returnValue(expectedScript));

            one(expectedScript).setContextClassloader(testClassLoader);
            
            one(expectedScript).setScriptSource(source);

            one(scriptRunnerFactoryMock).create(expectedScript);
            will(returnValue(expectedScriptRunner));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createCompiler(source).compile(expectedScriptBaseClass));
    }

    @Test
    public void testWithCachedSourceFile() {
        context.checking(new Expectations() {{
            one(cacheRepositoryMock).getGlobalCache("scripts/class-name", expectedCacheProperties);
            will(returnValue(cacheMock));

            allowing(cacheMock).isValid();
            will(returnValue(true));

            one(scriptCompilationHandlerMock).loadScriptFromDir(source, testClassLoader, expectedScriptCacheDir, expectedScriptBaseClass);
            will(returnValue(expectedScript));

            one(expectedScript).setContextClassloader(testClassLoader);

            one(expectedScript).setScriptSource(source);

            one(scriptRunnerFactoryMock).create(expectedScript);
            will(returnValue(expectedScriptRunner));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createCompiler(source).compile(expectedScriptBaseClass));
    }

    @Test
    public void testUsesSuppliedClassLoader() {
        final ClassLoader classLoader = new ClassLoader() {
        };

        context.checking(new Expectations(){{
            one(cacheRepositoryMock).getGlobalCache("scripts/class-name", expectedCacheProperties);
            will(returnValue(cacheMock));

            allowing(cacheMock).isValid();
            will(returnValue(true));

            one(scriptCompilationHandlerMock).loadScriptFromDir(
                    source,
                    classLoader,
                    expectedScriptCacheDir, 
                    expectedScriptBaseClass);
            will(returnValue(expectedScript));

            one(expectedScript).setContextClassloader(classLoader);

            one(expectedScript).setScriptSource(source);

            one(scriptRunnerFactoryMock).create(expectedScript);
            will(returnValue(expectedScriptRunner));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createCompiler(source).setClassloader(classLoader).compile(expectedScriptBaseClass));
    }

    @Test
    public void testUsesSuppliedTransformerToGenerateCacheDir() {
        final Transformer transformer = context.mock(Transformer.class);
        final File expectedCacheDir = new TestFile(expectedScriptCacheDir.getParentFile(), "transformer_Script").createDir();

        context.checking(new Expectations(){{
            allowing(transformer).getId();
            will(returnValue("transformer"));

            one(cacheRepositoryMock).getGlobalCache("scripts/class-name", expectedCacheProperties);
            will(returnValue(cacheMock));

            allowing(cacheMock).isValid();
            will(returnValue(true));

            one(scriptCompilationHandlerMock).loadScriptFromDir(source, testClassLoader, expectedCacheDir,
                    expectedScriptBaseClass);
            will(returnValue(expectedScript));

            one(expectedScript).setContextClassloader(testClassLoader);

            one(expectedScript).setScriptSource(source);

            one(scriptRunnerFactoryMock).create(expectedScript);
            will(returnValue(expectedScriptRunner));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createCompiler(source).setTransformer(transformer).compile(expectedScriptBaseClass));
    }
}