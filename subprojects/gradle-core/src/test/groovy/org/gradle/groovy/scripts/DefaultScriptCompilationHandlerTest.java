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
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.GradleScriptException;
import org.gradle.api.InputStreamClassLoader;
import org.gradle.api.internal.artifacts.dsl.AbstractScriptTransformer;
import org.gradle.util.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
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

    static final String TEST_EXPECTED_SYSTEMPROP_VALUE = "somevalue";
    static final String TEST_EXPECTED_SYSTEMPROP_KEY = "somekey";

    private DefaultScriptCompilationHandler scriptCompilationHandler;

    private File scriptCacheDir;
    private File cachedFile;

    private ScriptSource scriptSource;
    private String scriptText;
    private String scriptClassName;
    private String scriptFileName;

    private InputStreamClassLoader classLoader;

    private Class<? extends Script> expectedScriptClass;

    private JUnit4Mockery context = new JUnit4Mockery();
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        File testProjectDir = tmpDir.dir("projectDir");
        classLoader = new InputStreamClassLoader();
        InputStream inputStream = this.getClass().getResourceAsStream("/org/gradle/api/ClasspathTester.dat");
        classLoader.loadClass("org.gradle.api.ClasspathTester", inputStream);
        scriptCompilationHandler = new DefaultScriptCompilationHandler();
        scriptCacheDir = new File(testProjectDir, "cache");
        scriptText = "System.setProperty('" + TEST_EXPECTED_SYSTEMPROP_KEY + "', '" + TEST_EXPECTED_SYSTEMPROP_VALUE
                + "')";

        scriptClassName = "ScriptClassName";
        scriptFileName = "script-file-name";
        scriptSource = scriptSource();
        cachedFile = new File(scriptCacheDir, scriptClassName + ".class");
        expectedScriptClass = TestBaseScript.class;
    }

    private ScriptSource scriptSource() {
        return scriptSource(scriptText);
    }

    private ScriptSource scriptSource(final String scriptText) {
        final ScriptSource source = context.mock(ScriptSource.class, scriptText);
        context.checking(new Expectations(){{
            allowing(source).getClassName();
            will(returnValue(scriptClassName));
            allowing(source).getFileName();
            will(returnValue(scriptFileName));
            allowing(source).getDisplayName();
            will(returnValue("script-display-name"));
            allowing(source).getText();
            will(returnValue(scriptText));
        }});
        return source;
    }

    @After
    public void tearDown() {
        System.getProperties().remove(TEST_EXPECTED_SYSTEMPROP_KEY);
    }

    @Test
    public void testCompileScriptToDir() {
        scriptCompilationHandler.compileScriptToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadScriptFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass);
        evaluateScript(script);
    }

    @Test
    public void testCompileScriptToDirWithWhitespaceOnly() {
        final ScriptSource scriptSource = scriptSource("// ignore me\n");
        scriptCompilationHandler.compileScriptToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadScriptFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass);
        assertThat(script, is(expectedScriptClass));
    }

    @Test
    public void testCompileScriptToDirWithEmptyScript() {
        final ScriptSource scriptSource = scriptSource("");
        scriptCompilationHandler.compileScriptToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadScriptFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass);
        assertThat(script, is(expectedScriptClass));
    }

    @Test
    public void testCreateScriptOnTheFly() {
        Script script = scriptCompilationHandler.compileScript(scriptSource, classLoader, null,
                expectedScriptClass);

        checkScriptClassesNotInCache();

        evaluateScript(script);
    }

    @Test
    public void testCreateScriptOnTheFlyWithWhitespaceOnlyScript() {
        Script script = scriptCompilationHandler.compileScript(new StringScriptSource("script",
                "// ignore me\n"), classLoader, null, expectedScriptClass);

        checkScriptClassesNotInCache();

        assertThat(script, is(expectedScriptClass));
    }

    @Test
    public void testCreateScriptOnTheFlyWithEmptyScript() {
        Script script = scriptCompilationHandler.compileScript(new StringScriptSource("script", ""), classLoader,
                null, expectedScriptClass);

        checkScriptClassesNotInCache();

        assertThat(script, is(expectedScriptClass));
    }

    @Test
    public void testLoadFromDirWhenNotAssignableToBaseClass() {
        scriptCompilationHandler.compileScriptToDir(scriptSource, classLoader, scriptCacheDir, null, Script.class);
        assertNull(scriptCompilationHandler.loadScriptFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass));
    }

    @Test
    public void testCompileToDirWithException() {
        ScriptSource source = new StringScriptSource("script", "\n\nnew HHHHJSJSJ jsj");
        try {
            scriptCompilationHandler.compileScriptToDir(source, classLoader, scriptCacheDir, null, expectedScriptClass);
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getScriptSource(), sameInstance(source));
            assertThat(e.getLineNumber(), equalTo(3));
        }
    }

    @Test
    public void testCreateScriptWithException() {
        ScriptSource source = new StringScriptSource("script", "\n\nnew HHHHJSJSJ jsj");
        try {
            scriptCompilationHandler.compileScript(source, classLoader, null, expectedScriptClass);
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getScriptSource(), sameInstance(source));
            assertThat(e.getLineNumber(), equalTo(3));
        }
    }

    @Test
    public void testCanVisitAndTransformScriptClass() {
        Transformer visitor = new AbstractScriptTransformer() {
            protected int getPhase() {
                return Phases.CANONICALIZATION;
            }

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

        Script script = scriptCompilationHandler.compileScript(scriptSource("transformMe()"),
                classLoader, visitor, expectedScriptClass);
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
        assertEquals(script.getClass().getSimpleName(), scriptClassName);
        System.setProperty(TEST_EXPECTED_SYSTEMPROP_KEY, "not the expected value");
        script.run();
        assertEquals(TEST_EXPECTED_SYSTEMPROP_VALUE, System.getProperty(TEST_EXPECTED_SYSTEMPROP_KEY));
    }

    public abstract static class TestBaseScript extends Script {
    }
}
