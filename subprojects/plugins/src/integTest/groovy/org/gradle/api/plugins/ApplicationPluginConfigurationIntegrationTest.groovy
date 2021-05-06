/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ScriptExecuter
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.internal.TextUtil
import spock.lang.Unroll

class ApplicationPluginConfigurationIntegrationTest extends AbstractIntegrationSpec {

    def "can configure using project extension"() {
        settingsFile << """
            rootProject.name = 'test'
        """

        file("src/main/java/test/Main.java") << """
            package test;
            public class Main {
                public static void main(String[] args) {
                    System.out.println("all good");
                }
            }
        """

        buildFile << """
            plugins {
                id("application")
            }
            application {
                mainClass = "test.Main"
            }
        """

        when:
        run("installDist")

        def out = new ByteArrayOutputStream()
        def executer = new ScriptExecuter()
        executer.workingDir = testDirectory
        executer.standardOutput = out
        executer.commandLine = "build/install/test/bin/test"

        then:
        executer.run().assertNormalExitValue()
        out.toString() == TextUtil.toPlatformLineSeparators("all good\n")
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    @Unroll
    def "can configure using project extension for main class and main module"() {
        settingsFile << """
            rootProject.name = 'test'
        """

        file("src/main/java/test/Main.java") << """
            package test;
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Module: " + Main.class.getModule().getName());
                }
            }
        """
        file("src/main/java/module-info.java") << "module test.main {}"

        buildFile << """
            plugins {
                id("application")
            }
            application {
                $configClass
                $configModule
            }
        """

        if (configClass == '') {
            // set the main class directly in the compile task
            buildFile << "compileJava { options.javaModuleMainClass.set('test.Main') }"
        }

        when:
        if (deprecation) {
            executer.expectDocumentedDeprecationWarning("The JavaApplication.setMainClassName(String) method has been deprecated. This is scheduled to be removed in Gradle 8.0. Use #getMainClass().set(...) instead. See https://docs.gradle.org/current/dsl/org.gradle.api.plugins.JavaApplication.html#org.gradle.api.plugins.JavaApplication:mainClass for more details.")
        }
        run("installDist")

        def out = new ByteArrayOutputStream()
        def executer = new ScriptExecuter()
        executer.workingDir = testDirectory
        executer.standardOutput = out
        executer.commandLine = "build/install/test/bin/test"

        then:
        executer.run().assertNormalExitValue()
        out.toString() == TextUtil.toPlatformLineSeparators("Module: $expectedModule\n")

        where:
        configClass                   | configModule                  | expectedModule | deprecation
        "mainClassName = 'test.Main'" | ''                            | 'null'         | true
        "mainClass.set('test.Main')"  | ''                            | 'null'         | false
        "mainClass.set('test.Main')"  | "mainModule.set('test.main')" | 'test.main'    | false
        ''                            | "mainModule.set('test.main')" | 'test.main'    | false
    }
}
