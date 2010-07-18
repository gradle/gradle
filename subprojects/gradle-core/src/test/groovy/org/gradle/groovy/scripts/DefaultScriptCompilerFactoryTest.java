/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.internal.resource.Resource;
import org.gradle.logging.StandardOutputCapture;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.util.GUtil;
import org.gradle.util.HashUtil;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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

    Mockery context = new JUnit4Mockery();

    Class expectedScriptBaseClass = groovy.lang.Script.class;
    Map<String, Object> expectedCacheProperties;

    ScriptSource source;
    ScriptSource expectedSource;
    ScriptRunner expectedScriptRunner;
    CacheBuilder cacheBuilder;

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
        expectedScriptRunner = context.mock(ScriptRunner.class);
        scriptProcessor = new DefaultScriptCompilerFactory(scriptCompilationHandlerMock, scriptRunnerFactoryMock, cacheRepositoryMock);
        source = context.mock(ScriptSource.class);
        cacheBuilder = context.mock(CacheBuilder.class);

        context.checking(new Expectations() {{
            Resource resource = context.mock(Resource.class);

            allowing(source).getDisplayName();
            will(returnValue("[script source]"));
            allowing(source).getClassName();
            will(returnValue("class-name"));
            allowing(source).getFileName();
            will(returnValue("file-name"));
            allowing(source).getResource();
            will(returnValue(resource));
            allowing(resource).getText();
            will(returnValue(TEST_SCRIPT_TEXT));

            allowing(cacheMock).getBaseDir();
            will(returnValue(cacheDir));
        }});

        expectedSource = new CachingScriptSource(source);
        String expectedHash = HashUtil.createHash(TEST_SCRIPT_TEXT);
        expectedCacheProperties = GUtil.map("source.filename", "file-name", "source.hash", expectedHash);

        originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(testClassLoader);
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    @Test
    public void testWithSourceFileNotCached() {
        final Collector<TestScript> collector = collector();

        context.checking(new Expectations() {{
            one(cacheRepositoryMock).cache("scripts/class-name");
            will(returnValue(cacheBuilder));

            one(cacheBuilder).withProperties(expectedCacheProperties);
            will(returnValue(cacheBuilder));

            one(cacheBuilder).open();
            will(returnValue(cacheMock));

            allowing(cacheMock).isValid();
            will(returnValue(false));

            one(scriptCompilationHandlerMock).compileToDir(expectedSource, testClassLoader, expectedScriptCacheDir, null,
                    expectedScriptBaseClass);

            one(cacheMock).markValid();

            one(scriptCompilationHandlerMock).loadFromDir(expectedSource, testClassLoader, expectedScriptCacheDir,
                    expectedScriptBaseClass);
            will(returnValue(TestScript.class));

            one(scriptRunnerFactoryMock).create(with(notNullValue(TestScript.class)));
            will(collectTo(collector).then(returnValue(expectedScriptRunner)));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createCompiler(source).compile(expectedScriptBaseClass));
        assertSame(testClassLoader, collector.get().getContextClassloader());
        assertEquals(expectedSource, collector.get().getScriptSource());
    }

    @Test
    public void testWithCachedSourceFile() {
        final Collector<TestScript> collector = collector();

        context.checking(new Expectations() {{
            one(cacheRepositoryMock).cache("scripts/class-name");
            will(returnValue(cacheBuilder));

            one(cacheBuilder).withProperties(expectedCacheProperties);
            will(returnValue(cacheBuilder));

            one(cacheBuilder).open();
            will(returnValue(cacheMock));

            allowing(cacheMock).isValid();
            will(returnValue(true));

            one(scriptCompilationHandlerMock).loadFromDir(expectedSource, testClassLoader, expectedScriptCacheDir, expectedScriptBaseClass);
            will(returnValue(TestScript.class));

            one(scriptRunnerFactoryMock).create(with(notNullValue(TestScript.class)));
            will(collectTo(collector).then(returnValue(expectedScriptRunner)));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createCompiler(source).compile(expectedScriptBaseClass));
        assertSame(testClassLoader, collector.get().getContextClassloader());
        assertEquals(expectedSource, collector.get().getScriptSource());
    }

    @Test
    public void testUsesSuppliedClassLoaderToLoadScript() {
        final Collector<TestScript> collector = collector();
        final ClassLoader classLoader = new ClassLoader() {
        };

        context.checking(new Expectations(){{
            one(cacheRepositoryMock).cache("scripts/class-name");
            will(returnValue(cacheBuilder));

            one(cacheBuilder).withProperties(expectedCacheProperties);
            will(returnValue(cacheBuilder));

            one(cacheBuilder).open();
            will(returnValue(cacheMock));

            allowing(cacheMock).isValid();
            will(returnValue(true));

            one(scriptCompilationHandlerMock).loadFromDir(expectedSource, classLoader, expectedScriptCacheDir,
                    expectedScriptBaseClass);
            will(returnValue(TestScript.class));

            one(scriptRunnerFactoryMock).create(with(notNullValue(TestScript.class)));
            will(collectTo(collector).then(returnValue(expectedScriptRunner)));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createCompiler(source).setClassloader(classLoader).compile(expectedScriptBaseClass));
        assertSame(classLoader, collector.get().getContextClassloader());
    }

    @Test
    public void testUsesSuppliedTransformerToDetermineCacheDirName() {
        final Transformer transformer = context.mock(Transformer.class);
        final File expectedCacheDir = new TestFile(expectedScriptCacheDir.getParentFile(), "transformer_Script").createDir();

        context.checking(new Expectations(){{
            allowing(transformer).getId();
            will(returnValue("transformer"));

            one(cacheRepositoryMock).cache("scripts/class-name");
            will(returnValue(cacheBuilder));

            one(cacheBuilder).withProperties(expectedCacheProperties);
            will(returnValue(cacheBuilder));

            one(cacheBuilder).open();
            will(returnValue(cacheMock));

            allowing(cacheMock).isValid();
            will(returnValue(true));

            one(scriptCompilationHandlerMock).loadFromDir(expectedSource, testClassLoader, expectedCacheDir,
                    expectedScriptBaseClass);
            will(returnValue(TestScript.class));

            one(scriptRunnerFactoryMock).create(with(notNullValue(TestScript.class)));
            will(returnValue(expectedScriptRunner));
        }});

        assertSame(expectedScriptRunner, scriptProcessor.createCompiler(source).setTransformer(transformer).compile(expectedScriptBaseClass));
    }

    public static class TestScript extends Script {
        @Override
        public StandardOutputCapture getStandardOutputCapture() {
            return null;
        }

        @Override
        public void init(Object target, ServiceRegistry services) {
        }

        @Override
        public Object run() {
            return null;
        }
    }
}