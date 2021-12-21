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

package org.gradle.configurationcache

import org.gradle.process.ShellScript

class ConfigurationCacheExternalProcessInstrumentationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    // Note that all tests use a relative path to the script because its absolute path may contain
    // spaces and it breaks logic String.execute which splits the given string at spaces without
    // any options to escape the space.
    ShellScript baseScript = ShellScript.builder().printEnvironmentVariable('FOOBAR').printWorkingDir().writeTo(testDirectory, "test")

    def setup() {
        testDirectory.createDir(pwd)
    }

    def "#title is intercepted in groovy build script"(VarInitializer varInitializer) {
        given:
        def cwd = testDirectory.file(expectedPwdSuffix)
        buildFile("""
        import org.codehaus.groovy.runtime.ProcessGroovyMethods
        import static org.codehaus.groovy.runtime.ProcessGroovyMethods.execute

        ${varInitializer.getGroovy(baseScript.getRelativeCommandLine(cwd))}
        def process = $processCreator
        process.waitForProcessOutput(System.out, System.err)
        """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("FOOBAR=$expectedEnvVar\nCWD=${cwd.path}")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': external process started")
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
        fromString()       | "command.execute(null, null)"                                                         | ""                | ""
        fromString()       | "command.execute(['FOOBAR=foobar'], null)"                                            | ""                | "foobar"
        fromString()       | "command.execute(new String[] {'FOOBAR=foobar'}, null)"                               | ""                | "foobar"
        fromString()       | "command.execute(null, file('$pwd'))"                                                 | pwd               | ""
        // Typed nulls
        fromString()       | "command.execute((String[]) null, null)"                                              | ""                | ""
        fromString()       | "command.execute(null, (File) null)"                                                  | ""                | ""
        fromString()       | "command.execute((String[]) null, (File) null)"                                       | ""                | ""
        // type-wrapped arguments
        fromString()       | "command.execute((String[]) ['FOOBAR=foobar'], null)"                                 | ""                | "foobar"
        fromString()       | "command.execute((List) ['FOOBAR=foobar'], null)"                                     | ""                | "foobar"
        fromString()       | "command.execute(['FOOBAR=foobar'] as String[], null)"                                | ""                | "foobar"
        fromString()       | "command.execute(['FOOBAR=foobar'] as List, null)"                                    | ""                | "foobar"
        // null-safe call
        fromGroovyString() | "command?.execute()"                                                                  | ""                | ""

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
        fromString()       | "ProcessGroovyMethods.execute(command, null, null)"                                   | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], null)"                      | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, null)"         | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, null, file('$pwd'))"                           | pwd               | ""
        // Typed nulls
        fromString()       | "ProcessGroovyMethods.execute(command, (String[]) null, null)"                        | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, null, (File) null)"                            | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, (String[]) null, (File) null)"                 | ""                | ""
        // type-wrapped arguments
        fromGroovyString() | "ProcessGroovyMethods.execute(command as String)"                                     | ""                | ""
        fromGroovyString() | "ProcessGroovyMethods.execute(command as String, null, null)"                         | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, (String[]) ['FOOBAR=foobar'], null)"           | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, (List) ['FOOBAR=foobar'], null)"               | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'] as String[], null)"          | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'] as List, null)"              | ""                | "foobar"

        // static import calls (are handled differently by the dynamic Groovy's codegen)
        fromString()       | "execute(command)"                                                                    | ""                | ""
        fromGroovyString() | "execute(command)"                                                                    | ""                | ""
        fromStringArray()  | "execute(command)"                                                                    | ""                | ""
        fromStringList()   | "execute(command)"                                                                    | ""                | ""
        fromObjectList()   | "execute(command)"                                                                    | ""                | ""
        fromString()       | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                      | pwd               | "foobar"
        fromGroovyString() | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                      | pwd               | "foobar"
        fromStringArray()  | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                      | pwd               | "foobar"
        fromStringList()   | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                      | pwd               | "foobar"
        fromObjectList()   | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                      | pwd               | "foobar"
        fromString()       | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                   | pwd               | "foobar"
        fromGroovyString() | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                   | pwd               | "foobar"
        fromStringArray()  | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                   | pwd               | "foobar"
        fromStringList()   | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                   | pwd               | "foobar"
        fromObjectList()   | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                   | pwd               | "foobar"
        // Null argument handling
        fromString()       | "execute(command, null, null)"                                                        | ""                | ""
        fromString()       | "execute(command, ['FOOBAR=foobar'], null)"                                           | ""                | "foobar"
        fromString()       | "execute(command, new String[] {'FOOBAR=foobar'}, null)"                              | ""                | "foobar"
        fromString()       | "execute(command, null, file('$pwd'))"                                                | pwd               | ""
        // Typed nulls
        fromString()       | "execute(command, (String[]) null, null)"                                             | ""                | ""
        fromString()       | "execute(command, null, (File) null)"                                                 | ""                | ""
        fromString()       | "execute(command, (String[]) null, (File) null)"                                      | ""                | ""
        // type-wrapped arguments
        fromGroovyString() | "execute(command as String)"                                                          | ""                | ""
        fromGroovyString() | "execute(command as String, null, null)"                                              | ""                | ""
        fromString()       | "execute(command, (String[]) ['FOOBAR=foobar'], null)"                                | ""                | "foobar"
        fromString()       | "execute(command, (List) ['FOOBAR=foobar'], null)"                                    | ""                | "foobar"
        fromString()       | "execute(command, ['FOOBAR=foobar'] as String[], null)"                               | ""                | "foobar"
        fromString()       | "execute(command, ['FOOBAR=foobar'] as List, null)"                                   | ""                | "foobar"

        // Runtime.exec() overloads
        fromString()       | "Runtime.getRuntime().exec(command)"                                                  | ""                | ""
        fromGroovyString() | "Runtime.getRuntime().exec(command)"                                                  | ""                | ""
        fromStringArray()  | "Runtime.getRuntime().exec(command)"                                                  | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})"                  | ""                | "foobar"
        fromGroovyString() | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})"                  | ""                | "foobar"
        fromStringArray()  | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})"                  | ""                | "foobar"
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        fromGroovyString() | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        fromStringArray()  | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        // Null argument handling
        fromString()       | "Runtime.getRuntime().exec(command, null)"                                            | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, null)"            | ""                | "foobar"
        fromString()       | "Runtime.getRuntime().exec(command, null, file('$pwd'))"                              | pwd               | ""
        fromString()       | "Runtime.getRuntime().exec(command, null, null)"                                      | ""                | ""
        // Typed nulls
        fromString()       | "Runtime.getRuntime().exec(command, null as String[])"                                | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(command, null, null as File)"                              | ""                | ""
        // type-wrapped arguments
        fromGroovyString() | "Runtime.getRuntime().exec(command as String)"                                        | ""                | ""
        fromGroovyString() | "Runtime.getRuntime().exec(command as String, null)"                                  | ""                | ""
        fromGroovyString() | "Runtime.getRuntime().exec(command as String, null, null)"                            | ""                | ""
        fromObjectList()   | "Runtime.getRuntime().exec(command as String[])"                                      | ""                | ""
        fromObjectList()   | "Runtime.getRuntime().exec(command as String[], null)"                                | ""                | ""
        fromObjectList()   | "Runtime.getRuntime().exec(command as String[], null, null)"                          | ""                | ""
        // Null-safe calls
        fromString()       | "Runtime.getRuntime()?.exec(command)"                                                 | ""                | ""
        fromString()       | "Runtime.getRuntime()?.exec(command, new String[] {'FOOBAR=foobar'})"                 | ""                | "foobar"
        fromString()       | "Runtime.getRuntime()?.exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"   | pwd               | "foobar"

        // ProcessBuilder.start()
        fromStringArray()  | "new ProcessBuilder(command).start()"                                                 | ""                | ""
        fromStringList()   | "new ProcessBuilder(command).start()"                                                 | ""                | ""
        fromStringArray()  | "new ProcessBuilder(command)?.start()"                                                | ""                | ""

        title = processCreator.replace("command", varInitializer.description)
    }

    def "#title is intercepted in static groovy build script"(VarInitializer varInitializer) {
        given:
        def cwd = testDirectory.file(expectedPwdSuffix)
        buildFile("""
        import org.codehaus.groovy.runtime.ProcessGroovyMethods
        import static org.codehaus.groovy.runtime.ProcessGroovyMethods.execute

        @groovy.transform.CompileStatic
        void runStuff() {
            ${varInitializer.getGroovy(baseScript.getRelativeCommandLine(cwd))}
            def process = $processCreator
            process.waitForProcessOutput(System.out, System.err)
        }

        runStuff()
        """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("FOOBAR=$expectedEnvVar\nCWD=${cwd.path}")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': external process started")
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

    def "#title is intercepted in java build code"(VarInitializer varInitializer) {
        given:
        def cwd = testDirectory.file(expectedPwdSuffix)
        file("buildSrc/src/main/java/SneakyPlugin.java") << """
        import org.gradle.api.*;
        import java.io.*;
        import java.util.*;
        import org.codehaus.groovy.runtime.ProcessGroovyMethods;

        public class SneakyPlugin implements Plugin<Project> {
            @Override
            public void apply(Project project) {
                try {
                    ${varInitializer.getJava(baseScript.getRelativeCommandLine(cwd))}
                    Process process = $processCreator;
                    ProcessGroovyMethods.waitForProcessOutput(process, (OutputStream) System.out, (OutputStream) System.err);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        """
        buildFile("""
            apply plugin: SneakyPlugin
        """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("FOOBAR=$expectedEnvVar\nCWD=${cwd.path}")
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin class 'SneakyPlugin': external process started")
        }

        where:
        varInitializer    | processCreator                                                                                    | expectedPwdSuffix | expectedEnvVar
        // Direct ProcessGroovyMethods calls
        fromString()      | "ProcessGroovyMethods.execute(command)"                                                           | ""                | ""
        fromStringArray() | "ProcessGroovyMethods.execute(command)"                                                           | ""                | ""
        fromStringList()  | "ProcessGroovyMethods.execute(command)"                                                           | ""                | ""
        fromObjectList()  | "ProcessGroovyMethods.execute(command)"                                                           | ""                | ""
        fromString()      | "ProcessGroovyMethods.execute(command, new String[] {\"FOOBAR=foobar\"}, project.file(\"$pwd\"))" | pwd               | "foobar"
        fromStringArray() | "ProcessGroovyMethods.execute(command, new String[] {\"FOOBAR=foobar\"}, project.file(\"$pwd\"))" | pwd               | "foobar"
        fromStringList()  | "ProcessGroovyMethods.execute(command, new String[] {\"FOOBAR=foobar\"}, project.file(\"$pwd\"))" | pwd               | "foobar"
        fromObjectList()  | "ProcessGroovyMethods.execute(command, new String[] {\"FOOBAR=foobar\"}, project.file(\"$pwd\"))" | pwd               | "foobar"
        fromString()      | "ProcessGroovyMethods.execute(command, Arrays.asList(\"FOOBAR=foobar\"), project.file(\"$pwd\"))" | pwd               | "foobar"
        fromStringArray() | "ProcessGroovyMethods.execute(command, Arrays.asList(\"FOOBAR=foobar\"), project.file(\"$pwd\"))" | pwd               | "foobar"
        fromStringList()  | "ProcessGroovyMethods.execute(command, Arrays.asList(\"FOOBAR=foobar\"), project.file(\"$pwd\"))" | pwd               | "foobar"
        fromObjectList()  | "ProcessGroovyMethods.execute(command, Arrays.asList(\"FOOBAR=foobar\"), project.file(\"$pwd\"))" | pwd               | "foobar"
        // Null argument handling
        fromString()      | "ProcessGroovyMethods.execute(command, (List) null, null)"                                        | ""                | ""
        fromString()      | "ProcessGroovyMethods.execute(command, (String[]) null, null)"                                    | ""                | ""
        fromString()      | "ProcessGroovyMethods.execute(command, Arrays.asList(\"FOOBAR=foobar\"), null)"                   | ""                | "foobar"
        fromString()      | "ProcessGroovyMethods.execute(command, new String[] {\"FOOBAR=foobar\"}, null)"                   | ""                | "foobar"
        fromString()      | "ProcessGroovyMethods.execute(command, (List) null, project.file(\"$pwd\"))"                      | pwd               | ""
        fromString()      | "ProcessGroovyMethods.execute(command, (String[]) null, project.file(\"$pwd\"))"                  | pwd               | ""

        // Runtime.exec() overloads
        fromString()      | "Runtime.getRuntime().exec(command)"                                                              | ""                | ""
        fromStringArray() | "Runtime.getRuntime().exec(command)"                                                              | ""                | ""
        fromString()      | "Runtime.getRuntime().exec(command, new String[] {\"FOOBAR=foobar\"})"                            | ""                | "foobar"
        fromStringArray() | "Runtime.getRuntime().exec(command, new String[] {\"FOOBAR=foobar\"})"                            | ""                | "foobar"
        fromString()      | "Runtime.getRuntime().exec(command, new String[] {\"FOOBAR=foobar\"}, project.file(\"$pwd\"))"    | pwd               | "foobar"
        fromStringArray() | "Runtime.getRuntime().exec(command, new String[] {\"FOOBAR=foobar\"}, project.file(\"$pwd\"))"    | pwd               | "foobar"
        // Null argument handling
        fromString()      | "Runtime.getRuntime().exec(command, null)"                                                        | ""                | ""
        fromString()      | "Runtime.getRuntime().exec(command, new String[] {\"FOOBAR=foobar\"}, null)"                      | ""                | "foobar"
        fromString()      | "Runtime.getRuntime().exec(command, null, project.file(\"$pwd\"))"                                | pwd               | ""
        fromString()      | "Runtime.getRuntime().exec(command, null, null)"                                                  | ""                | ""

        // ProcessBuilder.start()
        fromStringArray() | "new ProcessBuilder(command).start()"                                                             | ""                | ""
        fromStringList()  | "new ProcessBuilder(command).start()"                                                             | ""                | ""

        title = processCreator.replace("command", varInitializer.description)
    }

    def "calling an unrelated method is allowed in groovy build script"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        generateClassesWithClashingMethods()

        buildFile << """
        import java.io.*
        import static ProcessGroovyMethodsExecute.execute

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
        """

        when:
        configurationCacheRun("-q", ":help")

        then:
        configurationCache.assertStateStored()
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

    def "calling an unrelated method is allowed in in java build code"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        generateClassesWithClashingMethods()

        file("buildSrc/src/main/java/SneakyPlugin.java") << """
        import org.gradle.api.*;
        import java.io.*;
        import java.util.*;

        public class SneakyPlugin implements Plugin<Project> {
            @Override
            public void apply(Project project) {
                String[] envpArray = new String[] { "array" };
                List<?> envpList = Arrays.asList(envpArray);
                String[] commandArray = new String[] { "some", "string" };
                List<?> commandList = Arrays.asList(commandArray);

                ProcessGroovyMethodsExecute.execute("some string");
                ProcessGroovyMethodsExecute.execute("some string", envpArray, project.file("test"));
                ProcessGroovyMethodsExecute.execute("some string", envpList, project.file("test"));

                ProcessGroovyMethodsExecute.execute(commandArray);
                ProcessGroovyMethodsExecute.execute(commandArray, envpArray, project.file("test"));
                ProcessGroovyMethodsExecute.execute(commandArray, envpList, project.file("test"));

                ProcessGroovyMethodsExecute.execute(commandList);
                ProcessGroovyMethodsExecute.execute(commandList, envpArray, project.file("test"));
                ProcessGroovyMethodsExecute.execute(commandList, envpList, project.file("test"));

                RuntimeExec e = new RuntimeExec();
                e.exec("some string");
                e.exec("some string", envpArray);
                e.exec("some string", envpArray, project.file("test"));
                e.exec(commandArray);
                e.exec(commandArray, envpArray);
                e.exec(commandArray, envpArray, project.file("test"));

                ProcessBuilderStart s = new ProcessBuilderStart();
                s.start();
            }
        }
        """
        buildFile("""
            apply plugin: SneakyPlugin
        """)

        when:
        configurationCacheRun("-q", ":help")

        then:
        configurationCache.assertStateStored()
    }

    private void generateClassesWithClashingMethods() {
        def sourceFolder = testDirectory.createDir("buildSrc/src/main/java")

        sourceFolder.file("ProcessGroovyMethodsExecute.java") << """
            import java.io.*;
            import java.util.*;

            public class ProcessGroovyMethodsExecute {

                public static Process execute(String command) { return null; }
                public static Process execute(String command, String[] envp, File file) { return null; }
                public static Process execute(String command, List<?> envp, File file) { return null; }
                public static Process execute(String[] command) { return null; }
                public static Process execute(String[] command, String[] envp, File file) { return null; }
                public static Process execute(String[] command, List<?> envp, File file) { return null; }
                public static Process execute(List<?> command) { return null; }
                public static Process execute(List<?> command, String[] envp, File file) { return null; }
                public static Process execute(List<?> command, List<?> envp, File file) { return null; }
            }
        """

        sourceFolder.file("RuntimeExec.java") << """
            import java.io.*;
            import java.util.*;

            public class RuntimeExec {
                public Process exec(String command) { return null; }
                public Process exec(String command, String[] envp) { return null; }
                public Process exec(String command, String[] envp, File file) { return null; }
                public Process exec(String command, List<?> envp, File file) { return null; }
                public Process exec(String[] command) { return null; }
                public Process exec(String[] command, String[] envp) { return null; }
                public Process exec(String[] command, String[] envp, File file) { return null; }
            }
        """

        sourceFolder.file("ProcessBuilderStart.java") << """
            public class ProcessBuilderStart {
                public Process start() { return null; }
            }
        """
    }

    private static String getPwd() {
        return "tmp"
    }

    private abstract static class VarInitializer {
        final String description

        VarInitializer(String description) {
            this.description = description
        }

        String getGroovy(List<String> cmd) {
            throw new UnsupportedOperationException()
        }

        String getJava(List<String> cmd) {
            throw new UnsupportedOperationException()
        }

        String getKotlin(List<String> cmd) {
            throw new UnsupportedOperationException()
        }

        @Override
        String toString() {
            return description
        }
    }

    static VarInitializer fromString() {
        return new VarInitializer("String") {
            @Override
            String getGroovy(List<String> cmd) {
                return """String command = ${ShellScript.cmdToStringLiteral(cmd)} """
            }

            @Override
            String getJava(List<String> cmd) {
                return """String command = ${ShellScript.cmdToStringLiteral(cmd)};"""
            }

            @Override
            String getKotlin(List<String> cmd) {
                return """val command = ${ShellScript.cmdToStringLiteral(cmd)} """
            }
        }
    }

    static VarInitializer fromGroovyString() {
        return new VarInitializer("GString") {
            @Override
            String getGroovy(List<String> cmd) {
                return """
                        String rawCommand = ${ShellScript.cmdToStringLiteral(cmd)}
                        def command = "\${rawCommand.toString()}"
                    """
            }
        }
    }

    static VarInitializer fromStringArray() {
        return new VarInitializer("String[]") {
            @Override
            String getGroovy(List<String> cmd) {
                return """String[] command = [${ShellScript.cmdToVarargLiterals(cmd)}]"""
            }

            @Override
            String getJava(List<String> cmd) {
                return """String[] command = new String[] { ${ShellScript.cmdToVarargLiterals(cmd)} };"""
            }

            @Override
            String getKotlin(List<String> cmd) {
                return """val command = arrayOf(${ShellScript.cmdToVarargLiterals(cmd)}) """
            }
        }
    }

    static VarInitializer fromStringList() {
        return new VarInitializer("List<String>") {
            @Override
            String getGroovy(List<String> cmd) {
                return """
                        def command = [${ShellScript.cmdToVarargLiterals(cmd)}]
                    """
            }

            @Override
            String getJava(List<String> cmd) {
                return """
                    List<String> command = Arrays.asList(${ShellScript.cmdToVarargLiterals(cmd)});
                """
            }

            @Override
            String getKotlin(List<String> cmd) {
                return """
                    val command = listOf(${ShellScript.cmdToVarargLiterals(cmd)})
                """
            }
        }
    }

    static VarInitializer fromObjectList() {
        return new VarInitializer("List<Object>") {
            @Override
            String getGroovy(List<String> cmd) {
                return """
                    def someArgument = '--some-argument'
                    def command = [${ShellScript.cmdToVarargLiterals(cmd)}, "\${someArgument.toString()}"]
                    """
            }

            @Override
            String getJava(List<String> cmd) {
                return """
                    Object someArgument = new Object() {
                        public String toString() {
                            return "--some-argument";
                        }
                    };
                    List<Object> command = Arrays.<Object>asList(${ShellScript.cmdToVarargLiterals(cmd)}, someArgument);
                    """
            }

            @Override
            String getKotlin(List<String> cmd) {
                return """
                    val someArgument = object : Any() {
                        override fun toString(): String = "--some-argument"
                    }
                    val command = listOf<Any>(${ShellScript.cmdToVarargLiterals(cmd)}, someArgument)
                    """
            }
        }
    }
}
