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
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.gradle.api.GradleScriptException;
import org.gradle.api.InputStreamClassLoader;
import org.gradle.api.Project;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultScriptCompilationHandlerTest {

    static final String TEST_SCRIPT_NAME = "somename_craidle";
    static final String TEST_EXPECTED_SYSTEMPROP_VALUE = "somevalue";
    static final String TEST_EXPECTED_SYSTEMPROP_KEY = "somekey";

    private DefaultScriptCompilationHandler scriptCompilationHandler;

    private File testProjectDir;

    private File scriptCacheDir;
    private File cachedFile;

    private String testScript;
    private ScriptSource scriptSource;

    private InputStreamClassLoader classLoader;

    private Class<? extends Script> expectedScriptClass;

    private CachePropertiesHandler cachePropertiesHandlerMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        cachePropertiesHandlerMock = context.mock(CachePropertiesHandler.class);
        testProjectDir = HelperUtil.makeNewTestDir("projectdir");
        classLoader = new InputStreamClassLoader();
        InputStream inputStream = this.getClass().getResourceAsStream("/org/gradle/api/ClasspathTester.dat");
        classLoader.loadClass("org.gradle.api.ClasspathTester", inputStream);
        scriptCompilationHandler = new DefaultScriptCompilationHandler(cachePropertiesHandlerMock);
        scriptCacheDir = new File(testProjectDir, "cache");
        testScript = "System.setProperty('" + TEST_EXPECTED_SYSTEMPROP_KEY + "', '" + TEST_EXPECTED_SYSTEMPROP_VALUE + "')";
        scriptSource = new StringScriptSource("script", testScript);
        cachedFile = new File(scriptCacheDir, scriptSource.getClassName() + ".class");
        expectedScriptClass = TestBaseScript.class;
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
        System.getProperties().remove(TEST_EXPECTED_SYSTEMPROP_KEY);
    }

    @Test
    public void init() {
        assertSame(cachePropertiesHandlerMock, scriptCompilationHandler.getCachePropertyHandler());
    }
    
    @Test
    public void testWriteToCache() {
        context.checking(new Expectations() {{
            one(cachePropertiesHandlerMock).writeProperties(testScript, scriptCacheDir);
            one(cachePropertiesHandlerMock).getCacheState(testScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.VALID));
        }});
        scriptCompilationHandler.writeToCache(scriptSource, classLoader, scriptCacheDir, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadFromCache(scriptSource, classLoader, scriptCacheDir, expectedScriptClass);
        evaluateScript(script);
    }

    @Test
    public void testWriteToCacheAndLoadFromCacheWithWhitespaceOnly() {
        final String emptyScript = "// ignore me\n";
        StringScriptSource scriptSource = new StringScriptSource("script", emptyScript);
        context.checking(new Expectations() {{
            one(cachePropertiesHandlerMock).getCacheState(emptyScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.VALID));
            one(cachePropertiesHandlerMock).writeProperties(emptyScript, scriptCacheDir);
        }});

        scriptCompilationHandler.writeToCache(scriptSource, classLoader, scriptCacheDir, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadFromCache(scriptSource, classLoader, scriptCacheDir, expectedScriptClass);
        assertThat(script, is(expectedScriptClass));
    }

    @Test
    public void testWriteToCacheAndLoadFromCacheWithEmptyScript() {
        final String emptyScript = "";
        StringScriptSource scriptSource = new StringScriptSource("script", emptyScript);
        context.checking(new Expectations() {{
            one(cachePropertiesHandlerMock).getCacheState(emptyScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.VALID));
            one(cachePropertiesHandlerMock).writeProperties(emptyScript, scriptCacheDir);
        }});

        scriptCompilationHandler.writeToCache(scriptSource, classLoader, scriptCacheDir, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadFromCache(scriptSource, classLoader, scriptCacheDir, expectedScriptClass);
        assertThat(script, is(expectedScriptClass));
    }

    @Test public void testCreateScriptOnTheFly() {
        Script script = scriptCompilationHandler.createScriptOnTheFly(scriptSource, classLoader, expectedScriptClass);

        checkScriptClassesNotInCache();

        evaluateScript(script);
    }

    @Test public void testCreateScriptOnTheFlyWithWhitespaceOnlyScript() {
        Script script = scriptCompilationHandler.createScriptOnTheFly(new StringScriptSource("script", "// ignore me\n"), classLoader, expectedScriptClass);

        checkScriptClassesNotInCache();

        assertThat(script, is(expectedScriptClass));
    }

    @Test public void testCreateScriptOnTheFlyWithEmptyScript() {
        Script script = scriptCompilationHandler.createScriptOnTheFly(new StringScriptSource("script", ""), classLoader, expectedScriptClass);

        checkScriptClassesNotInCache();

        assertThat(script, is(expectedScriptClass));
    }

    @Test public void testLoadFromCacheWithInvalidCache() {
        context.checking(new Expectations() {{
            allowing(cachePropertiesHandlerMock).getCacheState(testScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.INVALID));
        }});
        assertNull(scriptCompilationHandler.loadFromCache(scriptSource, classLoader, scriptCacheDir, expectedScriptClass));
    }

    @Test public void testLoadFromCacheWhenNotAssignableToBaseClass() {
        context.checking(new Expectations() {{
            one(cachePropertiesHandlerMock).writeProperties(testScript, scriptCacheDir);
            allowing(cachePropertiesHandlerMock).getCacheState(testScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.VALID));
        }});
        scriptCompilationHandler.writeToCache(scriptSource, classLoader, scriptCacheDir, Script.class);
        assertNull(scriptCompilationHandler.loadFromCache(scriptSource, classLoader, scriptCacheDir, expectedScriptClass));
    }

    @Test public void testWriteToCacheWithException() {
        ScriptSource source = new StringScriptSource("script", "\n\nnew HHHHJSJSJ jsj");
        try {
            scriptCompilationHandler.writeToCache(source, classLoader, scriptCacheDir, expectedScriptClass);
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getScriptSource(), sameInstance(source));
            assertThat(e.getLineNumber(), equalTo(3));
        }
    }

    @Test public void testCreateScriptWithException() {
        ScriptSource source = new StringScriptSource("script", "\n\nnew HHHHJSJSJ jsj");
        try {
            scriptCompilationHandler.createScriptOnTheFly(source, classLoader, expectedScriptClass);
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getScriptSource(), sameInstance(source));
            assertThat(e.getLineNumber(), equalTo(3));
        }
    }

    @Test public void testCanVisitAndTransformScriptClass() {
        CompilationUnit.SourceUnitOperation visitor = new CompilationUnit.SourceUnitOperation() {
            @Override
            public void call(SourceUnit source) throws CompilationFailedException {
                source.getAST().getStatementBlock().visit(new CodeVisitorSupport() {
                    @Override
                    public void visitMethodCallExpression(MethodCallExpression call) {
                        call.setObjectExpression(new ClassExpression(ClassHelper.make(System.class)));
                        call.setMethod(new ConstantExpression("setProperty"));
                        ArgumentListExpression arguments = (ArgumentListExpression) call.getArguments();
                        arguments.addExpression(new ConstantExpression(TEST_EXPECTED_SYSTEMPROP_KEY));
                        arguments.addExpression(new ConstantExpression(TEST_EXPECTED_SYSTEMPROP_VALUE));
                    }
                });
            }
        };

        DefaultScriptCompilationHandler handler = new DefaultScriptCompilationHandler(cachePropertiesHandlerMock,
                visitor);
        Script script = handler.createScriptOnTheFly(new StringScriptSource("source", "transformMe()"), classLoader, expectedScriptClass);
        evaluateScript(script);
    }

    private void checkScriptClassesInCache() {
        assertTrue(scriptCacheDir.isDirectory());
        assertTrue(cachedFile.isFile());
    }

    private void checkScriptClassesNotInCache() {
        assertTrue(!scriptCacheDir.exists() || (scriptCacheDir.isDirectory() && scriptCacheDir.list().length == 0));
    }

    private void evaluateScript(Script script) {
        assertThat(script, instanceOf(expectedScriptClass));
        assertEquals(script.getClass().getSimpleName(), Project.EMBEDDED_SCRIPT_ID);
        System.setProperty(TEST_EXPECTED_SYSTEMPROP_KEY, "not the expected value");
        script.run();
        assertEquals(TEST_EXPECTED_SYSTEMPROP_VALUE, System.getProperty(TEST_EXPECTED_SYSTEMPROP_KEY));
    }

    public abstract static class TestBaseScript extends Script {
    }
}
