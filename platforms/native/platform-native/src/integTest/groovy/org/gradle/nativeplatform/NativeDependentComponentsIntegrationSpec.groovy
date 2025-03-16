/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.nativeplatform

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp

class NativeDependentComponentsIntegrationSpec extends AbstractInstalledToolChainIntegrationSpec {

    def helloWorldApp = new ExeWithLibraryUsingLibraryHelloWorldApp()

    def setup() {
        settingsFile << "rootProject.name = 'test'"

        buildFile << '''
            apply plugin: "cpp"
            model {
                components {
                    greetings(NativeLibrarySpec) {
                        binaries.all {
                            if (!org.gradle.internal.os.OperatingSystem.current().isWindows()) {
                                cppCompiler.args("-fPIC");
                            }
                        }
                    }
                    hello(NativeLibrarySpec) {
                        binaries.all {
                            lib library: 'greetings' , linkage: 'static'
                        }
                    }
                    main(NativeExecutableSpec) {
                        binaries.all {
                            lib library: 'hello'
                        }
                    }
                }
            }
        '''.stripIndent()

        helloWorldApp.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))
    }

    def "creates #taskPrefix dependents tasks"() {
        when:
        succeeds 'tasks'

        then:
        outputContains "${taskPrefix}DependentsGreetings"
        outputContains "${taskPrefix}DependentsGreetingsSharedLibrary"
        outputContains "${taskPrefix}DependentsGreetingsStaticLibrary"
        outputContains "${taskPrefix}DependentsHello"
        outputContains "${taskPrefix}DependentsHelloSharedLibrary"
        outputContains "${taskPrefix}DependentsHelloStaticLibrary"
        outputContains "${taskPrefix}DependentsMain"
        outputContains "${taskPrefix}DependentsMainExecutable"

        where:
        taskPrefix | _
        'assemble' | _
        'build'    | _
    }

    def "#task triggers expected tasks only"() {
        when:
        succeeds task

        then:
        executed(getExpectedTasks(task))
        notExecuted(getUnexpectedTasks(task))

        where:
        task                                       | _
        'assembleDependentsMainExecutable'         | _
        'assembleDependentsMain'                   | _
        'assembleDependentsHelloStaticLibrary'     | _
        'assembleDependentsHelloSharedLibrary'     | _
        'assembleDependentsHello'                  | _
        'assembleDependentsGreetingsStaticLibrary' | _
        'assembleDependentsGreetingsSharedLibrary' | _
        'assembleDependentsGreetings'              | _
    }

    private static String[] getExpectedTasks(String task) {
        switch(task) {
            case 'assembleDependentsMainExecutable':
                return [':mainExecutable']
            case 'assembleDependentsMain':
                return [':mainExecutable']
            case 'assembleDependentsHelloStaticLibrary':
                return [':helloStaticLibrary']
            case 'assembleDependentsHelloSharedLibrary':
                return [':helloSharedLibrary', ':mainExecutable']
            case 'assembleDependentsHello':
                return [':helloStaticLibrary', ':helloSharedLibrary', ':mainExecutable']
            case 'assembleDependentsGreetingsStaticLibrary':
                return [':greetingsStaticLibrary', ':helloStaticLibrary', ':helloSharedLibrary', ':mainExecutable']
            case 'assembleDependentsGreetingsSharedLibrary':
                return[':greetingsSharedLibrary']
            case 'assembleDependentsGreetings':
                return [':greetingsStaticLibrary', ':greetingsSharedLibrary', ':helloStaticLibrary', ':helloSharedLibrary', ':mainExecutable']
            default:
                return []
        }
    }

    private static String[] getUnexpectedTasks(String task) {
        switch(task) {
            case 'assembleDependentsHelloStaticLibrary':
                return [':mainExecutable']
            case 'assembleDependentsGreetingsStaticLibrary':
                return [':greetingsSharedLibrary']
            case 'assembleDependentsGreetingsSharedLibrary':
                return [':greetingsStaticLibrary', ':helloStaticLibrary', ':helloSharedLibrary', ':mainExecutable']
            default:
                return []
        }
    }

}
