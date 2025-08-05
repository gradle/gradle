/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture

class JavaExecEnablePreviewIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def "ongoing"() {
        def jdk = AvailableJavaHomes.jdk21
        given:
        buildFile """
            plugins { id("java") }

            compileJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
                options.enablePreview = true
            }

            tasks.register("run", JavaExec) {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }

                classpath = project.layout.files(compileJava)
                mainClass = "driver.Driver"
                // enablePreview = true
                jvmArgs("--enable-preview")
            }
        """

        javaFile "src/main/java/Driver.java", """
            package driver;
            public class Driver {
                // Scoped values were in preview in Java 21 (https://openjdk.org/jeps/446)
                private static final ScopedValue<String> VALUE = ScopedValue.newInstance();
                public static void main(String[] args) {
                    ScopedValue.runWhere(VALUE, "preview", () ->
                        System.out.println("Scoped value: " + VALUE.get()));
                }
            }
        """

        when:
        withInstallations(jdk).run("run", "-S")

        then:
        outputContains("Scoped value: preview")
    }
}
