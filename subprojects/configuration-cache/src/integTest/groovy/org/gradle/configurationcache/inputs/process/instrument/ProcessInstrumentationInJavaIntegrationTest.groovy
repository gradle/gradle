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

class ProcessInstrumentationInJavaIntegrationTest extends AbstractProcessInstrumentationIntegrationTest {
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

    def "calling an unrelated method is allowed in java build code"() {
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
}
