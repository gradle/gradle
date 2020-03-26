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

package org.gradle.java.compile.jpms.execution

import org.gradle.java.compile.jpms.AbstractJavaModuleCompileIntegrationTest

class JavaModuleExecutionIntegrationTest extends AbstractJavaModuleCompileIntegrationTest {

    def setup() {
        buildFile << """
            dependencies {
                implementation 'org:moda:1.0'
            }
        """
    }

    def "runs a module using the module path with the application plugin"() {
        given:
        buildFile.text = buildFile.text.replace('java-library', 'application')
        buildFile << """
            application {
                mainClass.set('consumer.MainModule')
                mainModule.set('consumer')
            }
        """
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        when:
        succeeds ':run'

        then:
        outputContains("Module Name: consumer")
        outputContains("Module Version: 1.0-beta2")
    }

    def "runs a module using the module path with main class defined in compile task"() {
        given:
        buildFile << """
            task run(type: JavaExec) {
                modularClasspathHandling.inferModulePath.set(true)
                classpath = files(jar) + configurations.runtimeClasspath
                mainModule.set('consumer')
            }
            tasks.compileJava.configure {
                options.javaModuleMainClass.set('consumer.MainModule')
            }
        """
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        when:
        succeeds ':run'

        then:
        outputContains("Module Name: consumer")
        outputContains("Module Version: 1.0-beta2")
    }

    def "runs a module using the module path with main class defined in run task"() {
        given:
        buildFile << """
            task run(type: JavaExec) {
                modularClasspathHandling.inferModulePath.set(true)
                classpath = files(jar) + configurations.runtimeClasspath
                mainModule.set('consumer')
                mainClass.set('consumer.MainModule')
            }
        """
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        when:
        succeeds ':run'

        then:
        outputContains("Module Name: consumer")
        outputContains("Module Version: 1.0-beta2")
    }

    def "runs a module using the module path in a generic task with main class defined in compile task"() {
        given:
        buildFile << """
            task run {
                dependsOn jar
                doLast {
                    project.javaexec {
                        modularClasspathHandling.inferModulePath.set(true)
                        classpath = files(jar) + configurations.runtimeClasspath
                        mainModule.set('consumer')
                    }
                }
            }
            tasks.compileJava.configure {
                options.javaModuleMainClass.set('consumer.MainModule')
            }
        """
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        when:
        succeeds ':run'

        then:
        outputContains("Module Name: consumer")
        outputContains("Module Version: 1.0-beta2")
    }

    def "runs a module using the module path with main class defined in a generic task"() {
        given:
        buildFile << """
            task run {
                dependsOn jar
                doLast {
                    project.javaexec {
                        modularClasspathHandling.inferModulePath.set(true)
                        classpath = files(jar) + configurations.runtimeClasspath
                        mainModule.set('consumer')
                        mainClass.set('consumer.MainModule')
                    }
                }
            }
        """
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        consumingModuleClass('moda.ModaClass')

        when:
        succeeds ':run'

        then:
        outputContains("Module Name: consumer")
        outputContains("Module Version: 1.0-beta2")
    }

}
