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

import org.gradle.api.internal.initialization.loadercache.ClassLoaderId
import org.gradle.api.internal.initialization.loadercache.DummyClassLoaderCache
import org.gradle.api.internal.project.ProjectScript
import org.gradle.configuration.ImportsReader
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.internal.Actions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildScriptTransformerSpec extends Specification {

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    def importsReader = Mock(ImportsReader) {
        getImportPackages() >> ([] as String[])
    }

    final DefaultScriptCompilationHandler scriptCompilationHandler = new DefaultScriptCompilationHandler(new AsmBackedEmptyScriptGenerator(), new DummyClassLoaderCache(), importsReader)
    final String classpathClosureName = "buildscript"

    File scriptCacheDir
    File metadataCacheDir
    private classLoaderId = Mock(ClassLoaderId)

    def setup() {
        File testProjectDir = tmpDir.createDir("projectDir");
        scriptCacheDir = new File(testProjectDir, "cache");
        metadataCacheDir = new File(testProjectDir, "metadata");
    }

    private CompiledScript<Script, BuildScriptData> parse(String script) {
        def source = new StringScriptSource("test script", script)
        def loader = getClass().getClassLoader()
        def transformer = new BuildScriptTransformer(classpathClosureName, source)
        def operation = new FactoryBackedCompileOperation<BuildScriptData>("id", transformer, transformer, new BuildScriptDataSerializer())
        scriptCompilationHandler.compileToDir(source, loader, scriptCacheDir, metadataCacheDir, operation, ProjectScript, Actions.doNothing())
        return scriptCompilationHandler.loadFromDir(source, loader, scriptCacheDir, metadataCacheDir, operation, ProjectScript, classLoaderId)
    }

    def "empty script does not contain any code"() {
        expect:
        def scriptData = parse(script)
        scriptData.empty
        !scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods

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
        scriptData.empty
        !scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods
    }

    def "property declarations are not considered imperative code"() {
        given:
        def scriptData = parse("""
            String a
            String b = "hi"
            int c = b.length()
        """)

        expect:
        !scriptData.empty
        !scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods
    }

    def "field declarations are not considered imperative code"() {
        given:
        def scriptData = parse("""
            @groovy.transform.Field Long c
            @groovy.transform.Field Long d = 12
            @groovy.transform.Field Long e = d * 2
        """)

        expect:
        !scriptData.empty
        !scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods
    }

    def "extracted script blocks are not considered imperative code"() {
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
        scriptData.empty
        !scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods
    }

    def "model blocks are not considered imperative code"() {
        given:
        def scriptData = parse("""
model {
    task { foo(Task) { println "hi" } }
}
""")

        expect:
        !scriptData.empty
        !scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods
    }

    def "imports are not considered imperative code"() {
        expect:
        def scriptData = parse("import java.lang.String")
        scriptData.empty
        !scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods
    }

    def "method declarations are not considered imperative code"() {
        expect:
        def scriptData = parse("def method() { println 'hi' }")
        !scriptData.empty
        !scriptData.data.hasImperativeStatements
        scriptData.data.hasMethods
    }

    def "constant expressions and constant return are not imperative"() {
        expect:
        def scriptData = parse(script)
        !scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods

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

    def "imperative code is detected"() {
        expect:
        def scriptData = parse(script)
        !scriptData.empty
        scriptData.data.hasImperativeStatements
        !scriptData.data.hasMethods

        where:
        script                        | _
        "foo = 'bar'"                 | _
        "foo"                         | _
        '"${foo}"'                    | _
        "println 'hi!'"               | _
        "return a + 1"                | _
        "return foo"                  | _
        'return "${foo}"'             | _
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
