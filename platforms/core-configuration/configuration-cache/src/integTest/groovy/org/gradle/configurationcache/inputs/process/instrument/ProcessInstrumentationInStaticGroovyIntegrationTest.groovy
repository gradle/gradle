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

class ProcessInstrumentationInStaticGroovyIntegrationTest extends AbstractProcessInstrumentationIntegrationTest {
     def "#title is intercepted in static groovy build script"(VarInitializer varInitializer) {
        given:
        def cwd = testDirectory.file(expectedPwdSuffix)
        def command = varInitializer.getGroovy(baseScript.getRelativeCommandLine(cwd)).trim()

        buildFile("""
        import org.codehaus.groovy.runtime.ProcessGroovyMethods
        import static org.codehaus.groovy.runtime.ProcessGroovyMethods.execute

        @groovy.transform.CompileStatic
        void runStuff() {
            $command
            def process = $processCreator
            process.waitForProcessOutput(System.out, System.err)
        }

        runStuff()
        """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("FOOBAR=$expectedEnvVar")
        failure.assertOutputContains("CWD=${cwd.path}")
        problems.assertFailureHasProblems(failure) {
            def line = 7 + command.readLines().size()
            withProblem("Build file 'build.gradle': line $line: external process started")
        }

        where:
        varInitializer     | processCreator                                                                        | expectedPwdSuffix | expectedEnvVar
        fromString()       | "command.execute()"                                                                   | ""                | ""
        fromGroovyString() | "command.execute()"                                                                   | ""                | ""
        fromStringArray()  | "command.execute()"                                                                   | ""                | ""
        fromStringList()   | "command.execute()"                                                                   | ""                | ""
        fromObjectList()   | "command.execute()"                                                                   | ""                | ""
        fromString()       | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                       | pwd               | "foobar"
        fromGroovyString() | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                       | pwd               | "foobar"
        fromStringArray()  | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                       | pwd               | "foobar"
        fromStringList()   | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                       | pwd               | "foobar"
        fromObjectList()   | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                       | pwd               | "foobar"
        fromString()       | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                    | pwd               | "foobar"
        fromGroovyString() | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                    | pwd               | "foobar"
        fromStringArray()  | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                    | pwd               | "foobar"
        fromStringList()   | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                    | pwd               | "foobar"
        fromObjectList()   | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                    | pwd               | "foobar"
        // Null argument handling
        fromString()       | "command.execute((List) null, null)"                                                  | ""                | ""
        fromString()       | "command.execute((String[]) null, null)"                                              | ""                | ""
        fromString()       | "command.execute((List) null, file('$pwd'))"                                          | pwd               | ""
        fromString()       | "command.execute((String[]) null, file('$pwd'))"                                      | pwd               | ""
        fromString()       | "command.execute(['FOOBAR=foobar'], null)"                                            | ""                | "foobar"
        fromString()       | "command.execute(new String[] {'FOOBAR=foobar'}, null)"                               | ""                | "foobar"

        // Direct ProcessGroovyMethods calls
        fromString()       | "ProcessGroovyMethods.execute(command)"                                               | ""                | ""
        fromGroovyString() | "ProcessGroovyMethods.execute(command)"                                               | ""                | ""
        fromStringArray()  | "ProcessGroovyMethods.execute(command)"                                               | ""                | ""
        fromStringList()   | "ProcessGroovyMethods.execute(command)"                                               | ""                | ""
        fromObjectList()   | "ProcessGroovyMethods.execute(command)"                                               | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))" | pwd               | "foobar"
        fromGroovyString() | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))" | pwd               | "foobar"
        fromStringArray()  | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))" | pwd               | "foobar"
        fromStringList()   | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))" | pwd               | "foobar"
        fromObjectList()   | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))" | pwd               | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"              | pwd               | "foobar"
        fromGroovyString() | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"              | pwd               | "foobar"
        fromStringArray()  | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"              | pwd               | "foobar"
        fromStringList()   | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"              | pwd               | "foobar"
        fromObjectList()   | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"              | pwd               | "foobar"
        // Null argument handling
        fromString()       | "ProcessGroovyMethods.execute(command, (List) null, null)"                            | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, (String[]) null, null)"                        | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], null)"                      | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, null)"         | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, (List) null, file('$pwd'))"                    | pwd               | ""
        fromString()       | "ProcessGroovyMethods.execute(command, (String[]) null, file('$pwd'))"                | pwd               | ""

        // Runtime.exec() overloads
        fromString()       | "Runtime.getRuntime().exec(command)"                                                  | ""                | ""
        fromStringArray()  | "Runtime.getRuntime().exec(command)"                                                  | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})"                  | ""                | "foobar"
        fromStringArray()  | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})"                  | ""                | "foobar"
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        fromStringArray()  | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        // Null argument handling
        fromString()       | "Runtime.getRuntime().exec(command, null)"                                            | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, null)"            | ""                | "foobar"
        fromString()       | "Runtime.getRuntime().exec(command, null, file('$pwd'))"                              | pwd               | ""
        fromString()       | "Runtime.getRuntime().exec(command, null, null)"                                      | ""                | ""

        // ProcessBuilder.start()
        fromStringArray()  | "new ProcessBuilder(command).start()"                                                 | ""                | ""
        fromStringList()   | "new ProcessBuilder(command).start()"                                                 | ""                | ""

        title = processCreator.replace("command", varInitializer.description)
    }

     def "calling an unrelated method is allowed in static groovy build script"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        generateClassesWithClashingMethods()

        buildFile << """
        import java.io.*
        import static ProcessGroovyMethodsExecute.execute

        @groovy.transform.CompileStatic
        def runStuff() {
            ProcessGroovyMethodsExecute.execute("some string")
            ProcessGroovyMethodsExecute.execute("some string", ["array"] as String[], file("test"))
            ProcessGroovyMethodsExecute.execute("some string", ["array"], file("test"))

            ProcessGroovyMethodsExecute.execute(["some", "string"] as String[])
            ProcessGroovyMethodsExecute.execute(["some", "string"] as String[], ["array"] as String[], file("test"))
            ProcessGroovyMethodsExecute.execute(["some", "string"] as String[], ["array"], file("test"))

            ProcessGroovyMethodsExecute.execute(["some", "string"])
            ProcessGroovyMethodsExecute.execute(["some", "string"], ["array"] as String[], file("test"))
            ProcessGroovyMethodsExecute.execute(["some", "string"], ["array"], file("test"))

            execute("some string")
            execute("some string", ["array"] as String[], file("test"))
            execute("some string", ["array"], file("test"))

            execute(["some", "string"] as String[])
            execute(["some", "string"] as String[], ["array"] as String[], file("test"))
            execute(["some", "string"] as String[], ["array"], file("test"))

            execute(["some", "string"])
            execute(["some", "string"], ["array"] as String[], file("test"))
            execute(["some", "string"], ["array"], file("test"))

            def e = new RuntimeExec()
            e.exec("some string")
            e.exec("some string", ["array"] as String[])
            e.exec("some string", ["array"] as String[], file("test"))
            e.exec(["some", "string"] as String[])
            e.exec(["some", "string"] as String[], ["array"] as String[])
            e.exec(["some", "string"] as String[], ["array"] as String[], file("test"))


            def s = new ProcessBuilderStart()
            s.start()
        }

        runStuff()
        """

        when:
        configurationCacheRun("-q", ":help")

        then:
        configurationCache.assertStateStored()
    }
}
