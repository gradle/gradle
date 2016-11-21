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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaLibraryCompilationIntegrationTest extends AbstractIntegrationSpec {

    def "project can declare an API dependency"() {
        given:
        subproject('a') {
            'build.gradle'('''
                apply plugin: org.gradle.api.plugins.JavaLibraryPlugin
                dependencies {
                    api project(':b')
                }
            ''')
            src {
                main {
                    java {
                        'ToolImpl.java'('public class ToolImpl implements Tool { public void execute() {} }')
                    }
                }
            }
        }

        subproject('b') {
            'build.gradle'('''
                apply plugin: org.gradle.api.plugins.JavaLibraryPlugin
            ''')
            src {
                main {
                    java {
                        'Tool.java'('public interface Tool { void execute(); }')
                    }
                }
            }
        }

        when:
        succeeds 'a:compileJava'

        then:
        executedAndNotSkipped ':b:compileJava'
        notExecuted ':b:processResources', ':b:classes', ':b:jar'
    }

    def "uses the default configuration when producer is not a library"() {
        given:
        subproject('a') {
            'build.gradle'('''
                apply plugin: org.gradle.api.plugins.JavaLibraryPlugin
                dependencies {
                    api project(':b')
                }
            ''')
            src {
                main {
                    java {
                        'ToolImpl.java'('public class ToolImpl implements Tool { public void execute() {} }')
                    }
                }
            }
        }

        subproject('b') {
            'build.gradle'('''
                apply plugin: 'java'
            ''')
            src {
                main {
                    java {
                        'Tool.java'('public interface Tool { void execute(); }')
                    }
                }
            }
        }

        when:
        succeeds 'a:compileJava'

        then:
        executedAndNotSkipped ':b:compileJava', ':b:classes', ':b:jar'
        skipped ':b:processResources'
    }

    def "uses the API configuration when compiling a project against a library"() {
        given:
        subproject('a') {
            'build.gradle'('''
                apply plugin: 'java'
                dependencies {
                    compile project(':b')
                }
            ''')
            src {
                main {
                    java {
                        'ToolImpl.java'('public class ToolImpl implements Tool { public void execute() {} }')
                    }
                }
            }
        }

        subproject('b') {
            'build.gradle'('''
                apply plugin: org.gradle.api.plugins.JavaLibraryPlugin
            ''')
            src {
                main {
                    java {
                        'Tool.java'('public interface Tool { void execute(); }')
                    }
                }
            }
        }

        when:
        succeeds 'a:compileJava'

        then:
        executedAndNotSkipped ':b:compileJava'
        notExecuted ':b:processResources', ':b:classes', ':b:jar'
    }

    def "doesn't allow declaring dependencies using the 'compile' configuration"() {
        file('settings.gradle') << 'include "b"'
        buildFile << '''
            apply plugin: org.gradle.api.plugins.JavaLibraryPlugin

            dependencies {
                compile project(':b')
            }
        '''

        when:
        fails 'tasks'

        then:
        failure.assertHasCause "The 'compile' configuration should not be used to declare dependencies. Please use 'api' or 'implementation' instead."
    }

    private void subproject(String name, @DelegatesTo(value=FileTreeBuilder, strategy = Closure.DELEGATE_FIRST) Closure<Void> config) {
        file("settings.gradle") << "include '$name'\n"
        def subprojectDir = file(name)
        subprojectDir.mkdirs()
        FileTreeBuilder builder = new FileTreeBuilder(subprojectDir)
        config.setDelegate(builder)
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }
}
