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

import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class AbstractConfigurationCacheExternalProcessInstrumentationInDynamicGroovyIntegrationTest extends AbstractConfigurationCacheProcessInstrumentationIntegrationTest {
    abstract boolean enableIndy()

    def "#title is intercepted in groovy build script"(VarInitializer varInitializer) {
        given:
        def cwd = testDirectory.file(expectedPwdSuffix)
        withPluginCode("""
                import org.codehaus.groovy.runtime.ProcessGroovyMethods
                import static org.codehaus.groovy.runtime.ProcessGroovyMethods.execute
            """, """
                ${varInitializer.getGroovy(baseScript.getRelativeCommandLine(cwd))}
                def process = $processCreator
                process.waitForProcessOutput(System.out, System.err)
            """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("FOOBAR=$expectedEnvVar\nCWD=${cwd.path}")
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin class 'SomePlugin': external process started")
        }

        where:
        varInitializer     | processCreator                                                                           | expectedPwdSuffix | expectedEnvVar
        fromString()       | "command.execute()"                                                                      | ""                | ""
        fromGroovyString() | "command.execute()"                                                                      | ""                | ""
        fromStringArray()  | "command.execute()"                                                                      | ""                | ""
        fromStringList()   | "command.execute()"                                                                      | ""                | ""
        fromObjectList()   | "command.execute()"                                                                      | ""                | ""
        fromString()       | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                          | pwd               | "foobar"
        fromGroovyString() | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                          | pwd               | "foobar"
        fromStringArray()  | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                          | pwd               | "foobar"
        fromStringList()   | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                          | pwd               | "foobar"
        fromObjectList()   | "command.execute(new String[] {'FOOBAR=foobar'}, file('$pwd'))"                          | pwd               | "foobar"
        fromString()       | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                       | pwd               | "foobar"
        fromGroovyString() | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                       | pwd               | "foobar"
        fromStringArray()  | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                       | pwd               | "foobar"
        fromStringList()   | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                       | pwd               | "foobar"
        fromObjectList()   | "command.execute(['FOOBAR=foobar'], file('$pwd'))"                                       | pwd               | "foobar"
        // Null argument handling
        fromString()       | "command.execute(null, null)"                                                            | ""                | ""
        fromString()       | "command.execute(['FOOBAR=foobar'], null)"                                               | ""                | "foobar"
        fromString()       | "command.execute(new String[] {'FOOBAR=foobar'}, null)"                                  | ""                | "foobar"
        fromString()       | "command.execute(null, file('$pwd'))"                                                    | pwd               | ""
        // Spread calls
        fromString()       | "command.execute(*[new String[] {'FOOBAR=foobar'}, file('$pwd')])"                       | pwd               | "foobar"
        fromString()       | "command.execute(*[['FOOBAR=foobar'], file('$pwd')])"                                    | pwd               | "foobar"
        // Typed nulls
        fromString()       | "command.execute((String[]) null, null)"                                                 | ""                | ""
        fromString()       | "command.execute(null, (File) null)"                                                     | ""                | ""
        fromString()       | "command.execute((String[]) null, (File) null)"                                          | ""                | ""
        // type-wrapped arguments
        fromString()       | "command.execute((String[]) ['FOOBAR=foobar'], null)"                                    | ""                | "foobar"
        fromString()       | "command.execute((List) ['FOOBAR=foobar'], null)"                                        | ""                | "foobar"
        fromString()       | "command.execute(['FOOBAR=foobar'] as String[], null)"                                   | ""                | "foobar"
        fromString()       | "command.execute(['FOOBAR=foobar'] as List, null)"                                       | ""                | "foobar"
        // null-safe call
        fromGroovyString() | "command?.execute()"                                                                     | ""                | ""

        // Direct ProcessGroovyMethods calls
        fromString()       | "ProcessGroovyMethods.execute(command)"                                                  | ""                | ""
        fromGroovyString() | "ProcessGroovyMethods.execute(command)"                                                  | ""                | ""
        fromStringArray()  | "ProcessGroovyMethods.execute(command)"                                                  | ""                | ""
        fromStringList()   | "ProcessGroovyMethods.execute(command)"                                                  | ""                | ""
        fromObjectList()   | "ProcessGroovyMethods.execute(command)"                                                  | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        fromGroovyString() | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        fromStringArray()  | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        fromStringList()   | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        fromObjectList()   | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"    | pwd               | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"                 | pwd               | "foobar"
        fromGroovyString() | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"                 | pwd               | "foobar"
        fromStringArray()  | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"                 | pwd               | "foobar"
        fromStringList()   | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"                 | pwd               | "foobar"
        fromObjectList()   | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], file('$pwd'))"                 | pwd               | "foobar"
        // Null argument handling
        fromString()       | "ProcessGroovyMethods.execute(command, null, null)"                                      | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'], null)"                         | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, new String[] {'FOOBAR=foobar'}, null)"            | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, null, file('$pwd'))"                              | pwd               | ""
        // Spread calls
        fromString()       | "ProcessGroovyMethods.execute(*[command])"                                               | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(*[command, new String[] {'FOOBAR=foobar'}, file('$pwd')])" | pwd               | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(*[command, ['FOOBAR=foobar'], file('$pwd')])"              | pwd               | "foobar"
        // Typed nulls
        fromString()       | "ProcessGroovyMethods.execute(command, (String[]) null, null)"                           | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, null, (File) null)"                               | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, (String[]) null, (File) null)"                    | ""                | ""
        // type-wrapped arguments
        fromGroovyString() | "ProcessGroovyMethods.execute(command as String)"                                        | ""                | ""
        fromGroovyString() | "ProcessGroovyMethods.execute(command as String, null, null)"                            | ""                | ""
        fromString()       | "ProcessGroovyMethods.execute(command, (String[]) ['FOOBAR=foobar'], null)"              | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, (List) ['FOOBAR=foobar'], null)"                  | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'] as String[], null)"             | ""                | "foobar"
        fromString()       | "ProcessGroovyMethods.execute(command, ['FOOBAR=foobar'] as List, null)"                 | ""                | "foobar"

        // static import calls (are handled differently by the dynamic Groovy's codegen)
        fromString()       | "execute(command)"                                                                       | ""                | ""
        fromGroovyString() | "execute(command)"                                                                       | ""                | ""
        fromStringArray()  | "execute(command)"                                                                       | ""                | ""
        fromStringList()   | "execute(command)"                                                                       | ""                | ""
        fromObjectList()   | "execute(command)"                                                                       | ""                | ""
        fromString()       | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                         | pwd               | "foobar"
        fromGroovyString() | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                         | pwd               | "foobar"
        fromStringArray()  | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                         | pwd               | "foobar"
        fromStringList()   | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                         | pwd               | "foobar"
        fromObjectList()   | "execute(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"                         | pwd               | "foobar"
        fromString()       | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                      | pwd               | "foobar"
        fromGroovyString() | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                      | pwd               | "foobar"
        fromStringArray()  | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                      | pwd               | "foobar"
        fromStringList()   | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                      | pwd               | "foobar"
        fromObjectList()   | "execute(command, ['FOOBAR=foobar'], file('$pwd'))"                                      | pwd               | "foobar"
        // Null argument handling
        fromString()       | "execute(command, null, null)"                                                           | ""                | ""
        fromString()       | "execute(command, ['FOOBAR=foobar'], null)"                                              | ""                | "foobar"
        fromString()       | "execute(command, new String[] {'FOOBAR=foobar'}, null)"                                 | ""                | "foobar"
        fromString()       | "execute(command, null, file('$pwd'))"                                                   | pwd               | ""
        // Spread calls
        fromString()       | "execute(*[command])"                                                                    | ""                | ""
        fromString()       | "execute(*[command, new String[] {'FOOBAR=foobar'}, file('$pwd')])"                      | pwd               | "foobar"
        fromString()       | "execute(*[command, ['FOOBAR=foobar'], file('$pwd')])"                                   | pwd               | "foobar"
        // Typed nulls
        fromString()       | "execute(command, (String[]) null, null)"                                                | ""                | ""
        fromString()       | "execute(command, null, (File) null)"                                                    | ""                | ""
        fromString()       | "execute(command, (String[]) null, (File) null)"                                         | ""                | ""
        // type-wrapped arguments
        fromGroovyString() | "execute(command as String)"                                                             | ""                | ""
        fromGroovyString() | "execute(command as String, null, null)"                                                 | ""                | ""
        fromString()       | "execute(command, (String[]) ['FOOBAR=foobar'], null)"                                   | ""                | "foobar"
        fromString()       | "execute(command, (List) ['FOOBAR=foobar'], null)"                                       | ""                | "foobar"
        fromString()       | "execute(command, ['FOOBAR=foobar'] as String[], null)"                                  | ""                | "foobar"
        fromString()       | "execute(command, ['FOOBAR=foobar'] as List, null)"                                      | ""                | "foobar"

        // Runtime.exec() overloads
        fromString()       | "Runtime.getRuntime().exec(command)"                                                     | ""                | ""
        fromGroovyString() | "Runtime.getRuntime().exec(command)"                                                     | ""                | ""
        fromStringArray()  | "Runtime.getRuntime().exec(command)"                                                     | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})"                     | ""                | "foobar"
        fromGroovyString() | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})"                     | ""                | "foobar"
        fromStringArray()  | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'})"                     | ""                | "foobar"
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"       | pwd               | "foobar"
        fromGroovyString() | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"       | pwd               | "foobar"
        fromStringArray()  | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"       | pwd               | "foobar"
        // Null argument handling
        fromString()       | "Runtime.getRuntime().exec(command, null)"                                               | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(command, new String[] {'FOOBAR=foobar'}, null)"               | ""                | "foobar"
        fromString()       | "Runtime.getRuntime().exec(command, null, file('$pwd'))"                                 | pwd               | ""
        fromString()       | "Runtime.getRuntime().exec(command, null, null)"                                         | ""                | ""
        // Spread calls
        fromString()       | "Runtime.getRuntime().exec(*[command])"                                                  | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(*[command, new String[] {'FOOBAR=foobar'}])"                  | ""                | "foobar"
        fromString()       | "Runtime.getRuntime().exec(*[command, new String[] {'FOOBAR=foobar'}, file('$pwd')])"    | pwd               | "foobar"
        // Typed nulls
        fromString()       | "Runtime.getRuntime().exec(command, null as String[])"                                   | ""                | ""
        fromString()       | "Runtime.getRuntime().exec(command, null, null as File)"                                 | ""                | ""
        // type-wrapped arguments
        fromGroovyString() | "Runtime.getRuntime().exec(command as String)"                                           | ""                | ""
        fromGroovyString() | "Runtime.getRuntime().exec(command as String, null)"                                     | ""                | ""
        fromGroovyString() | "Runtime.getRuntime().exec(command as String, null, null)"                               | ""                | ""
        fromObjectList()   | "Runtime.getRuntime().exec(command as String[])"                                         | ""                | ""
        fromObjectList()   | "Runtime.getRuntime().exec(command as String[], null)"                                   | ""                | ""
        fromObjectList()   | "Runtime.getRuntime().exec(command as String[], null, null)"                             | ""                | ""
        // Null-safe calls
        fromString()       | "Runtime.getRuntime()?.exec(command)"                                                    | ""                | ""
        fromString()       | "Runtime.getRuntime()?.exec(command, new String[] {'FOOBAR=foobar'})"                    | ""                | "foobar"
        fromString()       | "Runtime.getRuntime()?.exec(command, new String[] {'FOOBAR=foobar'}, file('$pwd'))"      | pwd               | "foobar"

        // ProcessBuilder.start()
        fromStringArray()  | "new ProcessBuilder(command).start()"                                                    | ""                | ""
        fromStringList()   | "new ProcessBuilder(command).start()"                                                    | ""                | ""
        fromStringArray()  | "new ProcessBuilder(command)?.start()"                                                   | ""                | ""

        title = processCreator.replace("command", varInitializer.description)
    }

    def "calling an unrelated method is allowed in groovy build script"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        generateClassesWithClashingMethods()

        withPluginCode("""
                import java.io.*
                import static ProcessGroovyMethodsExecute.execute
            """,
            """
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
            """)

        when:
        configurationCacheRun("-q", ":help")

        then:
        configurationCache.assertStateStored()
    }

    private void withPluginCode(String imports, String codeUnderTest) {
        file("buildSrc/src/main/groovy/SomePlugin.groovy") << """
            import ${Plugin.name}
            import ${Project.name}

            $imports

            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tap {
                        $codeUnderTest
                    }
                }
            }
        """

        file("buildSrc/build.gradle") << """
        compileGroovy {
            groovyOptions.optimizationOptions.indy = ${enableIndy()}
        }
        """

        buildScript("""
            apply plugin: SomePlugin
        """)
    }
}
