/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.groovy.scripts.internal

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.internal.initialization.ClassLoaderIds
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId
import org.gradle.api.internal.initialization.loadercache.DummyClassLoaderCache
import org.gradle.configuration.ImportsReader
import org.gradle.groovy.scripts.ScriptCompilationException
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.groovy.scripts.Transformer
import org.gradle.internal.Actions
import org.gradle.internal.resource.Resource
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static org.hamcrest.Matchers.instanceOf
import static org.junit.Assert.*

class DefaultScriptCompilationHandlerTest extends Specification {

    static final String TEST_EXPECTED_SYSTEMPROP_VALUE = "somevalue"
    static final String TEST_EXPECTED_SYSTEMPROP_KEY = "somekey"

    private DefaultScriptCompilationHandler scriptCompilationHandler

    private File scriptCacheDir
    private File metadataCacheDir
    private File cachedFile

    private String scriptText
    private String scriptClassName
    private String scriptFileName

    private ClassLoader classLoader

    private Action<ClassNode> verifier = Actions.doNothing()

    private Class<? extends Script> expectedScriptClass

    private ImportsReader importsReader
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    @Rule
    public SetSystemProperties systemProperties = new SetSystemProperties()
    private final ClassLoaderId classLoaderId = ClassLoaderIds.buildScript("foo", "bar")

    def setup() {
        File testProjectDir = tmpDir.createDir("projectDir")
        classLoader = getClass().getClassLoader()
        importsReader = Stub(ImportsReader.class)
        scriptCompilationHandler = new DefaultScriptCompilationHandler(new DummyClassLoaderCache(), importsReader)
        scriptCacheDir = new File(testProjectDir, "cache")
        metadataCacheDir = new File(testProjectDir, "metadata")
        scriptText = "System.setProperty('" + TEST_EXPECTED_SYSTEMPROP_KEY + "', '" + TEST_EXPECTED_SYSTEMPROP_VALUE + "')"

        scriptClassName = "ScriptClassName"
        scriptFileName = "script-file-name"
        cachedFile = new File(scriptCacheDir, scriptClassName + ".class")
        expectedScriptClass = TestBaseScript.class
    }

    private ScriptSource scriptSource(final String scriptText) {
        def source = Stub(ScriptSource)
        def resource = Stub(Resource)
        _ * source.className >> scriptClassName
        _ * source.fileName >> scriptFileName
        _ * source.displayName >> "script-display-name"
        _ * source.resource >> resource
        _ * resource.text >> scriptText
        return source
    }

    def testCompileScriptToDir() {
        def scriptSource = scriptSource(scriptText)

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkScriptClassesInCache()

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, classLoaderId)

        then:
        compiledScript.runDoesSomething
        compiledScript.data == null
        Script script = compiledScript.loadClass().newInstance()
        evaluateScript(script)
    }

    def testCompileScriptToDirWithPackageDeclaration() {
        ScriptSource scriptSource = scriptSource("""package org.gradle.test
println 'hi'
""")

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        UnsupportedOperationException e = thrown()
        e.message == "Script-display-name should not contain a package statement."
    }

    def testCompileScriptToDirWithEmptyScript() {
        final ScriptSource scriptSource = scriptSource(emptyScript)

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkEmptyScriptInCache()

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, classLoaderId)

        then:
        !compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == null

        where:
        emptyScript                   | _
        ""                            | _
        "  \r\n\t   \n"               | _
        "\n // ignore me"             | _
        "/*\n\n*/"                    | _
        "import org.gradle.ignored.*" | _
    }

    def testCompileScriptToDirWithClassDefinitionOnlyScript() {
        final ScriptSource scriptSource = scriptSource("class SomeClass {}")

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkEmptyScriptInCache()

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, classLoaderId)

        then:
        !compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == null
    }

    def testCompileScriptToDirWithMethodOnlyScript() {
        final ScriptSource scriptSource = scriptSource("def method(def value) { return '[' + value + ']' }")

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkScriptClassesInCache(true)

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, classLoaderId)

        then:
        !compiledScript.runDoesSomething
        compiledScript.hasMethods
        compiledScript.data == null

        and:
        Script script = compiledScript.loadClass().newInstance()
        expectedScriptClass.isInstance(script)
        script.method(12) == "[12]"
    }

    def testCompileScriptToDirWithPropertiesOnlyScript() {
        final ScriptSource scriptSource = scriptSource("String a")

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkScriptClassesInCache(true)

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, classLoaderId)

        then:
        !compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == null
    }

    def testLoadFromDirWhenNotAssignableToBaseClass() {
        def scriptSource = scriptSource("ignoreMe = true")

        given:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, Script.class, verifier)

        when:
        scriptCompilationHandler.loadFromDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, classLoaderId).loadClass()

        then:
        GradleException e = thrown()
        e.message.contains("Could not load compiled classes for script-display-name from cache.")
        e.cause instanceof ClassCastException
    }

    def testCompileToDirWithSyntaxError() {
        ScriptSource source = new StringScriptSource("script.gradle", "\n\nnew HHHHJSJSJ jsj")

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        ScriptCompilationException e = thrown()
        e.lineNumber == 3
        e.cause.message.contains("script.gradle: 3: unexpected token: jsj")

        and:
        checkScriptCacheEmpty()
    }

    def testCanVisitAndTransformScriptClass() {
        def visitor = new AbstractScriptTransformer() {
            protected int getPhase() {
                return Phases.CANONICALIZATION
            }

            @Override
            public void call(SourceUnit source) throws CompilationFailedException {
                source.getAST().getStatementBlock().visit(new CodeVisitorSupport() {
                    @Override
                    public void visitMethodCallExpression(MethodCallExpression call) {
                        call.setObjectExpression(new ClassExpression(ClassHelper.make(System.class)))
                        call.setMethod(new ConstantExpression("setProperty"))
                        ArgumentListExpression arguments = (ArgumentListExpression) call.getArguments()
                        arguments.addExpression(new ConstantExpression(TEST_EXPECTED_SYSTEMPROP_KEY))
                        arguments.addExpression(new ConstantExpression(TEST_EXPECTED_SYSTEMPROP_VALUE))
                    }
                })
            }
        }

        def transformer = new CompileOperation<String>() {
            @Override
            public String getId() {
                return "id"
            }

            @Override
            public Transformer getTransformer() {
                return visitor
            }

            @Override
            public String getExtractedData() {
                return "extracted data"
            }

            @Override
            public Serializer<String> getDataSerializer() {
                return new BaseSerializerFactory().getSerializerFor(String)
            }
        }

        def source = scriptSource("transformMe()")

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, transformer, expectedScriptClass, verifier)
        def compiledScript = scriptCompilationHandler.loadFromDir(source, classLoader, scriptCacheDir, metadataCacheDir, transformer, expectedScriptClass, classLoaderId)

        then:
        compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == "extracted data"
        def script = compiledScript.loadClass().newInstance()
        evaluateScript(script)
    }

    def testCanTransformScriptClassToExtractDataAndRemoveStatements() {
        def visitor = new AbstractScriptTransformer() {
            protected int getPhase() {
                return Phases.CANONICALIZATION
            }

            @Override
            public void call(SourceUnit source) throws CompilationFailedException {
                source.getAST().getStatementBlock().getStatements().clear()
            }
        }

        def transformer = new CompileOperation<String>() {
            @Override
            public String getId() {
                return "id"
            }

            @Override
            public Transformer getTransformer() {
                return visitor
            }

            @Override
            public String getExtractedData() {
                return "extracted data"
            }

            @Override
            public Serializer<String> getDataSerializer() {
                return new BaseSerializerFactory().getSerializerFor(String)
            }
        }

        def source = scriptSource("transformMe()")

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, transformer, expectedScriptClass, verifier)
        def compiledScript = scriptCompilationHandler.loadFromDir(source, classLoader, scriptCacheDir, metadataCacheDir, transformer, expectedScriptClass, classLoaderId)

        then:
        !compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == "extracted data"
    }

    def testCanVisitAndTransformGeneratedClasses() {
        def verifier = Mock(Action)
        ScriptSource source = scriptSource("transformMe()")

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        1 * verifier.execute(!null)
    }

    private void checkScriptClassesInCache(boolean empty = false) {
        assertTrue(scriptCacheDir.isDirectory())
        assertTrue(cachedFile.isFile())
        checkEmptyScriptFlagSet(empty)
    }

    private void checkEmptyScriptInCache() {
        assertTrue(scriptCacheDir.isDirectory())
        checkEmptyScriptFlagSet(true)
    }

    private void checkScriptCacheEmpty() {
        assertFalse(scriptCacheDir.exists())
    }

    private void checkEmptyScriptFlagSet(boolean flag) {
        assertTrue(metadataCacheDir.isDirectory())
        def metaDataFile = new File(metadataCacheDir, "metadata.bin")
        assertTrue(metaDataFile.isFile())
        def decoder = new KryoBackedDecoder(new FileInputStream(metaDataFile))
        try {
            assertEquals(decoder.readByte() & 1, flag ? 1 : 0)
        } finally {
            decoder.close()
        }
    }

    private void evaluateScript(Script script) {
        assertThat(script, instanceOf(expectedScriptClass))
        assertEquals(script.getClass().getSimpleName(), scriptClassName)
        System.setProperty(TEST_EXPECTED_SYSTEMPROP_KEY, "not the expected value")
        script.run()
        assertEquals(TEST_EXPECTED_SYSTEMPROP_VALUE, System.getProperty(TEST_EXPECTED_SYSTEMPROP_KEY))
    }

    public abstract static class TestBaseScript extends Script {
    }
}
