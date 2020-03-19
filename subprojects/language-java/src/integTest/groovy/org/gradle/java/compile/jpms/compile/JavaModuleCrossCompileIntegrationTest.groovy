/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.java.compile.jpms.compile

import org.gradle.java.compile.jpms.AbstractJavaModuleIntegrationTest

class JavaModuleCrossCompileIntegrationTest extends AbstractJavaModuleIntegrationTest {

    def setup() {
        buildFile << """
            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
            // === This code is copied from Gradle code base, we need a public API for all this setup (adding a new directory set with compile task)
            // === https://github.com/gradle/gradle/issues/727
            def main = sourceSets.main
            def java9 = objects.sourceDirectorySet("java9", "Java 9 Sources")
            java9.srcDir("src/" + main.name + "/" + java9.name)
            main.allJava.source(java9)
            main.allSource.source(java9)
            def sourceSetChildPath = "classes/" + java9.name + "/" + main.name
            java9.destinationDirectory.convention(layout.buildDirectory.dir(sourceSetChildPath))
            def compileJava9 = tasks.register("compileJava9", JavaCompile) {
                sourceCompatibility = JavaVersion.VERSION_1_9.toString()
                targetCompatibility = JavaVersion.VERSION_1_9.toString()

                source = java9
                classpath = main.compileClasspath

                options.compilerArgs += ["--patch-module", "consumer=\${main.java.outputDir.path}"]
            }
            def sourceSetOutput = main.output as org.gradle.api.internal.tasks.DefaultSourceSetOutput
            sourceSetOutput.addClassesDir { java9.destinationDirectory.asFile.get() }
            sourceSetOutput.registerCompileTask(compileJava9)
            java9.compiledBy(compileJava9) {
                it.destinationDirectory
            }
            tasks.classes {
                dependsOn(compileJava9)
            }
        """
    }

    def "compiles a module that can be used as library with Java 8"() {
        given:
        file('src/main/java9/module-info.java').text = "module consumer { }"
        consumingModuleClass()

        when:
        succeeds ':classes'

        then:
        executed(':compileJava', ':compileJava9')
        javaClassFile('consumer/MainModule.class').exists()
        classFile('java9', 'main', 'module-info.class').exists()
    }

}
