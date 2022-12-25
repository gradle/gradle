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

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectScript
import org.gradle.configuration.ImportsReader
import org.gradle.configuration.ScriptTarget
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.Actions
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.Hashing
import org.gradle.internal.resource.StringTextResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildScriptTransformerSpec extends Specification {

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def importsReader = Mock(ImportsReader) {
        getImportPackages() >> ([] as String[])
        getSimpleNameToFullClassNamesMapping() >> [:]
    }

    final DefaultScriptCompilationHandler scriptCompilationHandler = new DefaultScriptCompilationHandler(
        TestFiles.deleter(),
        importsReader
    )

    File scriptCacheDir
    File metadataCacheDir

    def setup() {
        File testProjectDir = tmpDir.createDir("projectDir")
        scriptCacheDir = new File(testProjectDir, "cache")
        metadataCacheDir = new File(testProjectDir, "metadata")
    }

    private CompiledScript<Script, BuildScriptData> parse(String script) {
        def source = new TextResourceScriptSource(new StringTextResource("test script", script))
        def sourceHashCode = Hashing.hashString(script)
        def target = Mock(ScriptTarget) {
            getClasspathBlockName() >> "buildscript"
        }
        def targetScope = Stub(ClassLoaderScope) {
            createChild(_ as String) >> Stub(ClassLoaderScope)
        }
        def loader = getClass().getClassLoader()
        def transformer = new BuildScriptTransformer(source, target)
        def operation = new FactoryBackedCompileOperation<BuildScriptData>("id", 'stage', transformer, transformer, new BuildScriptDataSerializer())
        scriptCompilationHandler.compileToDir(source, loader, scriptCacheDir, metadataCacheDir, operation, ProjectScript, Actions.doNothing())
        return scriptCompilationHandler.loadFromDir(source, sourceHashCode, targetScope, DefaultClassPath.of(scriptCacheDir), metadataCacheDir, operation, ProjectScript)
    }

    def "empty script does not contain any code"() {
        expect:
        def scriptData = parse(script)
        !scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        !scriptData.hasMethods

        where:
        script         | _
        ""             | _
        "// ignore me" | _
        "\r\n\t   "    | _
    }

    def "class declarations are not considered imperative code"() {
        given:
        def scriptData = parse("""
            class SomeClass {
                String a = 123
                def doStuff() {
                    int i = 9
                }
            }
        """)

        expect:
        !scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        !scriptData.hasMethods
    }

    def "property declarations with constant initializer are not considered imperative code"() {
        given:
        def scriptData = parse("""
            String a
            String b = "hi"
            int c = 12
        """)

        expect:
        !scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        !scriptData.hasMethods
    }

    def "field declarations are not considered imperative code"() {
        given:
        def scriptData = parse("""
            @groovy.transform.Field Long c
            @groovy.transform.Field Long d = 12
            @groovy.transform.Field Long e = d * foo
        """)

        expect:
        !scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        !scriptData.hasMethods
    }

    def "filtered script blocks are not considered imperative code"() {
        given:
        def scriptData = parse("""
plugins {
    int v = 12
    println "ignore me"
}
buildscript {
    doStuff()
}
buildscript {
    if ( true ) { return }
}
""")

        expect:
        !scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        !scriptData.hasMethods
    }

    def "model blocks are not considered imperative code"() {
        given:
        def scriptData = parse("""
model {
    task { foo(Task) { println "hi" } }
}

model { thing { println "hi" } }
""")

        expect:
        scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        !scriptData.hasMethods
    }

    def "model blocks combined with other non imperative elements are not considered imperative code"() {
        given:
        def scriptData = parse("""
model {
    task { foo(Task) { println "hi" } }
}

"constant"

def something() { return 12 }

model { thing { println "hi" } }

class Thing { }

return null
""")

        expect:
        scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        scriptData.hasMethods
    }

    def "imports are not considered imperative code"() {
        expect:
        def scriptData = parse("""import java.lang.String
import java.lang.*
import static java.lang.String.*
""")
        !scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        !scriptData.hasMethods
    }

    def "method declarations are not considered imperative code"() {
        expect:
        def scriptData = parse("""def method() { println 'hi' }
private void doSomething() { thing = true }
""")
        !scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        scriptData.hasMethods
    }

    def "constant expressions and constant return are not imperative"() {
        expect:
        def scriptData = parse(script)
        !scriptData.runDoesSomething
        !scriptData.data.hasImperativeStatements
        !scriptData.hasMethods

        where:
        script         | _
        "return null"  | _
        "return true"  | _
        "return 'abc'" | _
        """
"hi"
'hi'
null
true
123
return 12
"""         | _
    }

    def "imperative code is detected in #script"() {
        expect:
        def scriptData = parse(script)
        scriptData.runDoesSomething
        scriptData.data.hasImperativeStatements
        !scriptData.hasMethods

        where:
        script                        | _
        "foo = 'bar'"                 | _
        "foo"                         | _
        '"${foo}"'                    | _
        "println 'hi!'"               | _
        "return a + 1"                | _
        "return foo"                  | _
        'return "${foo}"'             | _
        'String s = "a" + "b"'        | _
        "if (a) { return null }; foo" | _
        """
plugins {
}
println "hi"
"""                        | _
        """
foo
return null
"""                        | _
    }
}
