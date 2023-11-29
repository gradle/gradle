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
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.RootClassLoaderScope
import org.gradle.api.internal.initialization.loadercache.DummyClassLoaderCache
import org.gradle.api.problems.ProblemEmitter
import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.configuration.ImportsReader
import org.gradle.groovy.scripts.ScriptCompilationException
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.groovy.scripts.Transformer
import org.gradle.initialization.ClassLoaderScopeRegistryListener
import org.gradle.internal.Actions
import org.gradle.internal.Describables
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.resource.TextResource
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification

import static org.hamcrest.CoreMatchers.instanceOf
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class DefaultScriptCompilationHandlerTest extends Specification {

    static final String TEST_EXPECTED_SYSTEM_PROP_VALUE = "someValue"
    static final String TEST_EXPECTED_SYSTEM_PROP_KEY = "someKey"

    private DefaultScriptCompilationHandler scriptCompilationHandler

    private File scriptCacheDir
    private File metadataCacheDir
    private File cachedFile
    private ClassPath scriptClassPath

    private String scriptText
    private String scriptClassName
    private String scriptFileName

    private ClassLoader classLoader = getClass().getClassLoader()
    private ClassLoaderScope targetScope = new RootClassLoaderScope("test", getClass().classLoader, getClass().classLoader, new DummyClassLoaderCache(), Stub(ClassLoaderScopeRegistryListener))

    private Action<ClassNode> verifier = Actions.doNothing()

    private Class<? extends Script> expectedScriptClass

    private ImportsReader importsReader
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    @Rule
    public SetSystemProperties systemProperties = new SetSystemProperties()

    def setup() {
        def problemEmitter = Stub(ProblemEmitter)
        def problems = new DefaultProblems(problemEmitter)

        File testProjectDir = tmpDir.createDir("projectDir")
        importsReader = Stub(ImportsReader.class)
        scriptCompilationHandler = new DefaultScriptCompilationHandler(
            TestFiles.deleter(),
            importsReader
        ) {
            @Override
            protected Problems getProblemService() {
                return problems
            }
        }

        scriptCacheDir = new File(testProjectDir, "cache")
        scriptClassPath = DefaultClassPath.of(scriptCacheDir)
        metadataCacheDir = new File(testProjectDir, "metadata")
        scriptText = "System.setProperty('" + TEST_EXPECTED_SYSTEM_PROP_KEY + "', '" + TEST_EXPECTED_SYSTEM_PROP_VALUE + "')"

        scriptClassName = "ScriptClassName"
        scriptFileName = "script-file-name"
        cachedFile = new File(scriptCacheDir, scriptClassName + ".class")
        expectedScriptClass = TestBaseScript.class
    }

    private ScriptSource scriptSource(final String scriptText) {
        def source = Stub(ScriptSource)
        def resource = Stub(TextResource)
        _ * source.className >> scriptClassName
        _ * source.fileName >> scriptFileName
        _ * source.displayName >> "script-display-name"
        _ * source.longDisplayName >> Describables.of("script-display-name")
        _ * source.resource >> resource
        _ * resource.text >> scriptText
        return source
    }

    def testCompileScriptToDir() {
        def scriptSource = scriptSource(scriptText)
        def sourceHashCode = hashFor(scriptText)

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkScriptClassesInCache()

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, null, expectedScriptClass)

        then:
        compiledScript.runDoesSomething
        compiledScript.data == null
        Script script = JavaReflectionUtil.newInstance(compiledScript.loadClass())
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
        def sourceHashCode = hashFor(scriptText)

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkEmptyScriptInCache()

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, null, expectedScriptClass)

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
        def scriptText = "class SomeClass {}"
        final ScriptSource scriptSource = scriptSource(scriptText)
        def sourceHashCode = hashFor(scriptText)

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkEmptyScriptInCache()

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, null, expectedScriptClass)

        then:
        !compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == null
    }

    def testCompileScriptToDirWithMethodOnlyScript() {
        def scriptText = "def method(def value) { return '[' + value + ']' }"
        final ScriptSource scriptSource = scriptSource(scriptText)
        def sourceHashCode = hashFor(scriptText)

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkScriptClassesInCache(true)

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, null, expectedScriptClass)

        then:
        !compiledScript.runDoesSomething
        compiledScript.hasMethods
        compiledScript.data == null

        and:
        Script script = JavaReflectionUtil.newInstance(compiledScript.loadClass())
        expectedScriptClass.isInstance(script)
        script.method(12) == "[12]"
    }

    def testCompileScriptToDirWithPropertiesOnlyScript() {
        def scriptText = "String a"
        final ScriptSource scriptSource = scriptSource(scriptText)
        def sourceHashCode = hashFor(this.scriptText)

        when:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        checkScriptClassesInCache(true)

        when:
        def compiledScript = scriptCompilationHandler.loadFromDir(scriptSource, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, null, expectedScriptClass)

        then:
        !compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == null
    }

    def testLoadFromDirWhenNotAssignableToBaseClass() {
        def scriptText = "ignoreMe = true"
        def scriptSource = scriptSource(scriptText)
        def sourceHashCode = hashFor(this.scriptText)

        given:
        scriptCompilationHandler.compileToDir(scriptSource, classLoader, scriptCacheDir, metadataCacheDir, null, Script.class, verifier)

        when:
        scriptCompilationHandler.loadFromDir(scriptSource, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, null, expectedScriptClass).loadClass()

        then:
        GradleException e = thrown()
        e.message.contains("Could not load compiled classes for script-display-name from cache.")
        e.cause instanceof ClassCastException
    }

    def testCompileToDirWithSyntaxError() {
        ScriptSource source = new TextResourceScriptSource(new StringTextResource("script.gradle", "\n\nnew invalid syntax"))

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        ScriptCompilationException e = thrown()
        e.lineNumber == 3
        e.cause.message.contains("script.gradle: 3: Unexpected input: 'syntax' @ line 3, column 13")

        and:
        checkScriptCacheEmpty()
    }

    def testCanVisitAndTransformScriptClass() {
        def visitor = new AbstractScriptTransformer() {
            protected int getPhase() {
                return Phases.CANONICALIZATION
            }

            @Override
            void call(SourceUnit source) throws CompilationFailedException {
                source.getAST().getStatementBlock().visit(new CodeVisitorSupport() {
                    @Override
                    void visitMethodCallExpression(MethodCallExpression call) {
                        call.setObjectExpression(new ClassExpression(ClassHelper.make(System.class)))
                        call.setMethod(new ConstantExpression("setProperty"))
                        ArgumentListExpression arguments = (ArgumentListExpression) call.getArguments()
                        arguments.addExpression(new ConstantExpression(TEST_EXPECTED_SYSTEM_PROP_KEY))
                        arguments.addExpression(new ConstantExpression(TEST_EXPECTED_SYSTEM_PROP_VALUE))
                    }
                })
            }
        }

        def transformer = new CompileOperation<String>() {
            @Override
            String getId() {
                return "id"
            }

            @Override
            String getStage() {
                return "STAGE"
            }

            @Override
            Transformer getTransformer() {
                return visitor
            }


            @Override
            String getExtractedData() {
                return "extracted data"
            }

            @Override
            Serializer<String> getDataSerializer() {
                return new BaseSerializerFactory().getSerializerFor(String)
            }
        }

        def scriptText = "transformMe()"
        def source = scriptSource(scriptText)
        def sourceHashCode = hashFor(scriptText)

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, transformer, expectedScriptClass, verifier)
        def compiledScript = scriptCompilationHandler.loadFromDir(source, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, transformer, expectedScriptClass)

        then:
        compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == "extracted data"
        def script = JavaReflectionUtil.newInstance(compiledScript.loadClass())
        evaluateScript(script)
    }

    def testCanTransformScriptClassToExtractDataAndRemoveStatements() {
        def visitor = new AbstractScriptTransformer() {
            protected int getPhase() {
                return Phases.CANONICALIZATION
            }

            @Override
            void call(SourceUnit source) throws CompilationFailedException {
                source.getAST().getStatementBlock().getStatements().clear()
            }
        }

        def transformer = new CompileOperation<String>() {
            @Override
            String getId() {
                return "id"
            }

            @Override
            String getStage() {
                return "STAGE"
            }

            @Override
            Transformer getTransformer() {
                return visitor
            }

            @Override
            String getExtractedData() {
                return "extracted data"
            }

            @Override
            Serializer<String> getDataSerializer() {
                return new BaseSerializerFactory().getSerializerFor(String)
            }

        }

        def scriptText = "transformMe()"
        def source = scriptSource(scriptText)
        def sourceHashCode = hashFor(this.scriptText)

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, transformer, expectedScriptClass, verifier)
        def compiledScript = scriptCompilationHandler.loadFromDir(source, sourceHashCode, targetScope, scriptClassPath, metadataCacheDir, transformer, expectedScriptClass)

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

    @Issue('GRADLE-3382')
    def "test compile with #unknownClass"() {
        ScriptSource source = new TextResourceScriptSource(new StringTextResource("script.gradle", "new ${unknownClass}()"))

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        ScriptCompilationException e = thrown()
        e.lineNumber == 1
        e.cause.message.contains("script.gradle: 1: unable to resolve class ${unknownClass}")

        and:
        checkScriptCacheEmpty()

        where:
        unknownClass << ['unknownClass', 'fully.qualified.unknownClass', 'not.java.util.Map.Entry']
    }

    @Issue('GRADLE-3423')
    def testCompileWithInnerClassReference() {
        ScriptSource source = new TextResourceScriptSource(new StringTextResource("script.gradle", innerClass))

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        noExceptionThrown()

        where:
        innerClass << [
            "Map.Entry entry = null",
            "java.util.Map.Entry entry = null",
            """
import java.util.Map.Entry
Entry entry = null
""",
            """
class Outer {
    static class Inner { }
}
Outer.Inner entry = null
"""
        ]
    }

    @Issue('GRADLE-3423')
    @Ignore
    def testCompileWithInnerInnerClassReference() {
        ScriptSource source = new TextResourceScriptSource(new StringTextResource("script.gradle", innerClass))

        when:
        scriptCompilationHandler.compileToDir(source, classLoader, scriptCacheDir, metadataCacheDir, null, expectedScriptClass, verifier)

        then:
        noExceptionThrown()

        where:
        innerClass << [
            // fails
            "import javax.swing.text.html.HTMLDocument.HTMLReader.SpecialAction",
            // OK
            """
import javax.swing.text.html.HTMLDocument.HTMLReader
HTMLReader.SpecialAction action = null
""",
            "javax.swing.text.html.HTMLDocument.HTMLReader.SpecialAction action = null",
            """
class Outer {
    class Inner {
         class Deeper {
         }
    }
}
Outer.Inner.Deeper weMustGoDeeper = null
"""
        ]
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
        System.setProperty(TEST_EXPECTED_SYSTEM_PROP_KEY, "not the expected value")
        script.run()
        assertEquals(TEST_EXPECTED_SYSTEM_PROP_VALUE, System.getProperty(TEST_EXPECTED_SYSTEM_PROP_KEY))
    }

    abstract static class TestBaseScript extends Script {
    }

    private static HashCode hashFor(String scriptText) {
        Hashing.hashString(scriptText)
    }
}
