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

package org.gradle.groovy.scripts.internal;

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
import org.gradle.api.GradleException;
import org.gradle.api.internal.resource.Resource;
import org.gradle.groovy.scripts.ScriptCompilationException;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.gradle.util.Matchers.containsLine;
import static org.gradle.util.Matchers.isA;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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

    private ClassLoader classLoader;

    private Class<? extends Script> expectedScriptClass;

    private JUnit4Mockery context = new JUnit4Mockery();
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        File testProjectDir = tmpDir.createDir("projectDir");
        classLoader = getClass().getClassLoader();
        scriptCompilationHandler = new DefaultScriptCompilationHandler(new AsmBackedEmptyScriptGenerator());
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
            Resource resource = context.mock(Resource.class, scriptText + "resource");

            allowing(source).getClassName();
            will(returnValue(scriptClassName));
            allowing(source).getFileName();
            will(returnValue(scriptFileName));
            allowing(source).getDisplayName();
            will(returnValue("script-display-name"));
            allowing(source).getResource();
            will(returnValue(resource));
            allowing(resource).getText();
            will(returnValue(scriptText));
        }});
        return source;
    }

    @After
    public void tearDown() {
        System.getProperties().remove(TEST_EXPECTED_SYSTEMPROP_KEY);
    }

    @Test
    public void testCompileScriptToDir() throws Exception {
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass).newInstance();
        evaluateScript(script);
    }

    @Test
    public void testCompileScriptToDirWithPackageDeclaration() throws Exception {
        final ScriptSource scriptSource = scriptSource("package org.gradle.test\n" + scriptText);

        try {
            scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage(), equalTo("Script-display-name should not contain a package statement."));
        }
    }

    @Test
    public void testCompileScriptToDirWithWhitespaceOnly() throws Exception {
        final ScriptSource scriptSource = scriptSource("// ignore me\n");
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkEmptyScriptInCache();

        Script script = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass).newInstance();
        assertThat(script, isA(expectedScriptClass));
    }

    @Test
    public void testCompileScriptToDirWithEmptyScript() throws Exception {
        final ScriptSource scriptSource = scriptSource("");
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkEmptyScriptInCache();

        Script script = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass).newInstance();
        assertThat(script, isA(expectedScriptClass));
    }

    @Test
    public void testCompileScriptToDirWithClassDefinitionOnlyScript() throws Exception {
        final ScriptSource scriptSource = scriptSource("class SomeClass {}");
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkEmptyScriptInCache();

        Script script = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass).newInstance();
        assertThat(script, isA(expectedScriptClass));
    }

    @Test
    public void testCompileScriptToDirWithMethodOnlyScript() throws Exception {
        final ScriptSource scriptSource = scriptSource("def method() { println 'hi' }");
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass).newInstance();
        assertThat(script, isA(expectedScriptClass));
    }

    @Test
    public void testCompileScriptToDirWithPropertiesOnlyScript() throws Exception {
        final ScriptSource scriptSource = scriptSource("String a");
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, null, expectedScriptClass);

        checkScriptClassesInCache();

        Script script = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir,
                expectedScriptClass).newInstance();
        assertThat(script, isA(expectedScriptClass));
    }

    @Test
    public void testLoadFromDirWhenNotAssignableToBaseClass() {
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, null, Script.class);
        try {
            scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir,
                    expectedScriptClass);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), containsString("Could not load compiled classes for script-display-name from cache."));
            assertThat(e.getCause(), instanceOf(ClassCastException.class));
        }
    }

    @Test
    public void testCompileToDirWithSyntaxError() {
        ScriptSource source = new StringScriptSource("script.gradle", "\n\nnew HHHHJSJSJ jsj");
        try {
            scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, null, expectedScriptClass);
            fail();
        } catch (ScriptCompilationException e) {
            assertThat(e.getScriptSource(), sameInstance(source));
            assertThat(e.getLineNumber(), equalTo(3));
            assertThat(e.getCause().getMessage(), containsLine(startsWith("script.gradle: 3: unexpected token: jsj")));
        }

        checkScriptCacheEmpty();
    }

    @Test
    public void testCanVisitAndTransformScriptClass() throws Exception {
        Transformer visitor = new AbstractScriptTransformer() {
            public String getId() {
                return "id";
            }

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

        ScriptSource source = scriptSource("transformMe()");
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, visitor, expectedScriptClass);
        Script script = scriptCompilationHandler.loadFromDir(source, classLoader, scriptCacheDir, expectedScriptClass).newInstance();
        evaluateScript(script);
    }

    private void checkScriptClassesInCache() {
        assertTrue(scriptCacheDir.isDirectory());
        assertTrue(cachedFile.isFile());
        assertFalse(new File(scriptCacheDir, "emptyScript.txt").exists());
    }

    private void checkEmptyScriptInCache() {
        assertTrue(scriptCacheDir.isDirectory());
        assertTrue(new File(scriptCacheDir, "emptyScript.txt").isFile());
    }

    private void checkScriptCacheEmpty() {
        assertFalse(scriptCacheDir.exists());
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
