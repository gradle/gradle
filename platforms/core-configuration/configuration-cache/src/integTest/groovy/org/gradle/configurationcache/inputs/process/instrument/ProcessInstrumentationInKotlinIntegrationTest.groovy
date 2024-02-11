/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.inputs.process.instrument

class ProcessInstrumentationInKotlinIntegrationTest extends AbstractProcessInstrumentationIntegrationTest {
    def "#title is intercepted in kotlin build script"(VarInitializer varInitializer) {
        given:
        def cwd = testDirectory.file(expectedPwdSuffix)
        buildKotlinFile << """
        import java.io.OutputStream
        import org.codehaus.groovy.runtime.ProcessGroovyMethods

        ${varInitializer.getKotlin(baseScript.getRelativeCommandLine(cwd))}
        val process = $processCreator
        ProcessGroovyMethods.waitForProcessOutput(process, System.out as OutputStream, System.err as OutputStream)
        """

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("FOOBAR=$expectedEnvVar\nCWD=${cwd.path}")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle.kts': external process started")
        }

        where:
        varInitializer    | processCreator                                                                      | expectedPwdSuffix | expectedEnvVar
        // Direct ProcessGroovyMethods calls
        fromString()      | "ProcessGroovyMethods.execute(command)"                                             | ""                | ""
        fromStringArray() | "ProcessGroovyMethods.execute(command)"                                             | ""                | ""
        fromStringList()  | "ProcessGroovyMethods.execute(command)"                                             | ""                | ""
        fromObjectList()  | "ProcessGroovyMethods.execute(command)"                                             | ""                | ""
        fromString()      | "ProcessGroovyMethods.execute(command, arrayOf(\"FOOBAR=foobar\"), file(\"$pwd\"))" | pwd               | "foobar"
        fromStringArray() | "ProcessGroovyMethods.execute(command, arrayOf(\"FOOBAR=foobar\"), file(\"$pwd\"))" | pwd               | "foobar"
        fromStringList()  | "ProcessGroovyMethods.execute(command, arrayOf(\"FOOBAR=foobar\"), file(\"$pwd\"))" | pwd               | "foobar"
        fromObjectList()  | "ProcessGroovyMethods.execute(command, arrayOf(\"FOOBAR=foobar\"), file(\"$pwd\"))" | pwd               | "foobar"
        fromString()      | "ProcessGroovyMethods.execute(command, listOf(\"FOOBAR=foobar\"), file(\"$pwd\"))"  | pwd               | "foobar"
        fromStringArray() | "ProcessGroovyMethods.execute(command, listOf(\"FOOBAR=foobar\"), file(\"$pwd\"))"  | pwd               | "foobar"
        fromStringList()  | "ProcessGroovyMethods.execute(command, listOf(\"FOOBAR=foobar\"), file(\"$pwd\"))"  | pwd               | "foobar"
        fromObjectList()  | "ProcessGroovyMethods.execute(command, listOf(\"FOOBAR=foobar\"), file(\"$pwd\"))"  | pwd               | "foobar"
        // Null argument handling
        fromString()      | "ProcessGroovyMethods.execute(command, null as List<*>?, null)"                     | ""                | ""
        fromString()      | "ProcessGroovyMethods.execute(command, null as Array<String>?, null)"               | ""                | ""
        fromString()      | "ProcessGroovyMethods.execute(command, listOf(\"FOOBAR=foobar\"), null)"            | ""                | "foobar"
        fromString()      | "ProcessGroovyMethods.execute(command, arrayOf(\"FOOBAR=foobar\"), null)"           | ""                | "foobar"
        fromString()      | "ProcessGroovyMethods.execute(command, null as List<*>?, file(\"$pwd\"))"           | pwd               | ""
        fromString()      | "ProcessGroovyMethods.execute(command, null as Array<String>?, file(\"$pwd\"))"     | pwd               | ""

        // Runtime.exec() overloads
        fromString()      | "Runtime.getRuntime().exec(command)"                                                | ""                | ""
        fromStringArray() | "Runtime.getRuntime().exec(command)"                                                | ""                | ""
        fromString()      | "Runtime.getRuntime().exec(command, arrayOf(\"FOOBAR=foobar\"))"                    | ""                | "foobar"
        fromStringArray() | "Runtime.getRuntime().exec(command, arrayOf(\"FOOBAR=foobar\"))"                    | ""                | "foobar"
        fromString()      | "Runtime.getRuntime().exec(command, arrayOf(\"FOOBAR=foobar\"), file(\"$pwd\"))"    | pwd               | "foobar"
        fromStringArray() | "Runtime.getRuntime().exec(command, arrayOf(\"FOOBAR=foobar\"), file(\"$pwd\"))"    | pwd               | "foobar"
        // Null argument handling
        fromString()      | "Runtime.getRuntime().exec(command, null)"                                          | ""                | ""
        fromString()      | "Runtime.getRuntime().exec(command, arrayOf(\"FOOBAR=foobar\"), null)"              | ""                | "foobar"
        fromString()      | "Runtime.getRuntime().exec(command, null, file(\"$pwd\"))"                          | pwd               | ""
        fromString()      | "Runtime.getRuntime().exec(command, null, null)"                                    | ""                | ""

        // ProcessBuilder.start()
        fromStringArray() | "ProcessBuilder(*command).start()"                                                  | ""                | ""
        fromStringList()  | "ProcessBuilder(command).start()"                                                   | ""                | ""

        title = processCreator.replace("command", varInitializer.description)
    }

    def "calling an unrelated method is allowed in kotlin build script"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        generateClassesWithClashingMethods()

        buildKotlinFile("""
        ProcessGroovyMethodsExecute.execute("some string")
        ProcessGroovyMethodsExecute.execute("some string", arrayOf("array"), file("test"))
        ProcessGroovyMethodsExecute.execute("some string", listOf("array"), file("test"))

        ProcessGroovyMethodsExecute.execute(arrayOf("some", "string"))
        ProcessGroovyMethodsExecute.execute(arrayOf("some", "string"), arrayOf("string"), file("test"))
        ProcessGroovyMethodsExecute.execute(arrayOf("some", "string"), listOf("array"), file("test"))

        ProcessGroovyMethodsExecute.execute(listOf("some", "string"))
        ProcessGroovyMethodsExecute.execute(listOf("some", "string"), arrayOf("string"), file("test"))
        ProcessGroovyMethodsExecute.execute(listOf("some", "string"), listOf("array"), file("test"))

        val e = RuntimeExec()
        e.exec("some string")
        e.exec("some string", arrayOf("string"))
        e.exec("some string", arrayOf("string"), file("test"))
        e.exec(arrayOf("some", "string"))
        e.exec(arrayOf("some", "string"), arrayOf("string"))
        e.exec(arrayOf("some", "string"), arrayOf("string"), file("test"))

        val s = ProcessBuilderStart()
        s.start()

        """)

        when:
        configurationCacheRun("-q", ":help")

        then:
        configurationCache.assertStateStored()
    }
}
