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
            tasks.withType(JavaCompile).configureEach {
                // use the project's version as module version
                options.javaModuleVersion = provider { project.version }
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

    def "runs a module accessing resources using the module path with the application plugin"() {
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
        def mainClass = consumingModuleClass('moda.ModaClass')
        mainClass.text = mainClass.text.replace('.run()', '.run(); MainModule.class.getModule().getResourceAsStream("data.txt").readAllBytes()')
        file('src/main/resources/data.txt').text = "some data"

        when:
        succeeds ':run'

        then:
        outputContains("Module Name: consumer")
        outputContains("Module Version: 1.0-beta2")
    }

    // This test demonstrated the current behavior for how a module compilation of sources in one 'source directory set' and be patched with the result of another.
    // If we add higher level modeling concepts for the relationship between the compile steps on one source set, the '--patch-module' arguments could maybe be derived automatically.
    def "runs a module accessing classes from separate compilation step using the module path with the application plugin"() {
        given:
        buildFile.text = buildFile.text.replace('java-library', 'application')
        buildFile << """
            apply plugin: 'groovy'
            application {
                mainClass.set('consumer.MainModule')
                mainModule.set('consumer')
            }
            dependencies {
                implementation localGroovy()
            }
            // compile Groovy first
            compileGroovy {
                classpath = sourceSets.main.compileClasspath
            }
            // We need to patch the previously compiled classes (by the Groovy compile) into the module
            def patchArgs = ['--patch-module', "consumer=\${compileGroovy.destinationDirectory.getAsFile().get().path}"]
            compileJava {
                options.compilerArgs = patchArgs
                classpath += files(sourceSets.main.groovy.classesDirectory)
            }
        """
        publishJavaModule('moda')
        consumingModuleInfo('requires moda')
        file('src/main/groovy/consumer/GroovyInterface.groovy') << """
            package consumer;

            interface GroovyInterface { }
        """
        def mainClass = consumingModuleClass('moda.ModaClass')
        mainClass.text = mainClass.text.replace('MainModule {', 'MainModule implements GroovyInterface {')

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
            interface Services {
                @javax.inject.Inject ExecOperations getExec()
            }

            task run {
                dependsOn jar
                def execOperations = project.objects.newInstance(Services).exec
                doLast {
                    execOperations.javaexec { action ->
                        action.classpath = files(jar) + configurations.runtimeClasspath
                        action.mainModule.set('consumer')
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
            interface Services {
                @javax.inject.Inject ExecOperations getExec()
            }

            task run {
                dependsOn jar
                def execOperations = project.objects.newInstance(Services).exec
                doLast {
                    execOperations.javaexec { action ->
                        action.classpath = files(jar) + configurations.runtimeClasspath
                        action.mainModule.set('consumer')
                        action.mainClass.set('consumer.MainModule')
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

    def "does not run as module if module path inference is turned off"() {
        given:
        buildFile.text = buildFile.text.replace('java-library', 'application')
        buildFile << """
            tasks.run.modularity.inferModulePath = false
            application {
                mainClass.set('consumer.MainModule')
                mainModule.set('consumer')
            }
        """
        publishJavaModule('moda')
        consumingModuleInfo()
        consumingModuleClass()

        when:
        succeeds ':run'

        then:
        outputContains("Module Name: null")
        outputContains("Module Version: null")
    }
}
