/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests

import org.gradle.api.internal.plugins.ExecutableJar
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ScriptExecuter

/**
 * Tests for {@link org.gradle.api.internal.plugins.StartScriptGenerator} that are not covered by the application plugin tests, because they are not public API.
 */
class StartScriptGeneratorIntegrationTest extends AbstractIntegrationSpec {
    def "can create and execute a start script using an executable JAR"() {
        given:
        buildFile("""
            plugins {
                id("java")
            }

            tasks.jar {
                manifest {
                    attributes(
                        "Main-Class": "org.gradle.test.jarstarter.Main"
                    )
                }
            }

            abstract class GenerateStartScript extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getJarFile()

                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                def generate() {
                    def generator = new ${StartScriptGenerator.class.getName()}()
                    generator.setApplicationName("Jar Starter")
                    generator.setGitRef("6c9eca778c871a6310d2c3f2c3d3f8e67a915538")
                    generator.setOptsEnvironmentVar("JAR_STARTER_OPTS")
                    generator.setExitEnvironmentVar("JAR_STARTER_EXIT")
                    // The jar is at <output dir>/main.jar
                    generator.setEntryPoint(new ${ExecutableJar.class.getName()}("main.jar"))
                    // The script is at <output dir>/jar-starter
                    generator.setScriptRelPath("jar-starter")
                    // No classpath needed
                    generator.setClasspath([])
                    generator.setAppNameSystemProperty("org.gradle.jarstarter.appname")
                    generator.setDefaultJvmOpts([])


                    def unixScriptFile = outputDir.file("jar-starter").get().asFile
                    generator.generateUnixScript(unixScriptFile)

                    def windowsScriptFile = outputDir.file("jar-starter.bat").get().asFile
                    generator.generateWindowsScript(windowsScriptFile)
                }
            }

            def startScriptDir = project.layout.buildDirectory.dir("generated-start-script")

            tasks.register("copyJarFile", Copy) {
                from(tasks.jar)
                into(startScriptDir)
                rename { "main.jar" }
            }

            tasks.register("generateStartScript", GenerateStartScript) {
                dependsOn("copyJarFile")
                jarFile = tasks.jar.archiveFile
                outputDir = startScriptDir
            }
        """)
        file("src/main/java/org/gradle/test/jarstarter/Main.java") << """
            package org.gradle.test.jarstarter;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("JAR start successful.");
                }
            }
        """

        when:
        succeeds("generateStartScript")

        and:
        def outputCapture = new ByteArrayOutputStream()
        def scriptStarter = new ScriptExecuter()
        scriptStarter.workingDir = file('build/generated-start-script')
        scriptStarter.executable = "jar-starter"
        scriptStarter.standardOutput = outputCapture
        def result = scriptStarter.run()

        then:
        result.assertNormalExitValue()
        outputCapture.toString().contains("JAR start successful.")
    }
}
