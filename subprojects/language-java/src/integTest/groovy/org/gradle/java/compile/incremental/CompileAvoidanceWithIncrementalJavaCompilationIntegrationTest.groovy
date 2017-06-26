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

package org.gradle.java.compile.incremental

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompileAvoidanceWithIncrementalJavaCompilationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << '''
            allprojects {
                tasks.withType(JavaCompile) {
                    options.incremental = true
                }
                
                repositories {
                    jcenter()
                }
            }
        '''
    }

    @NotYetImplemented
    def "handles malformed jars"() {
        buildFile << """
            apply plugin: 'java'
            dependencies {
                compile files("broken.jar")
            }
        """
        file("broken.jar").text = "this is not a jar"
        file("src/main/java/Main.java") << "public class Main {}"
        expect:
        succeeds("compileJava")
    }

    def "doesn't recompile if implementation dependency changed in ABI compatible way"() {
        given:
        subproject('a') {
            'build.gradle'("""
                apply plugin: 'java'

                dependencies {
                    compile 'org.apache.commons:commons-math3:3.4'
                }
            """)
            src {
                main {
                    java {
                        'ToolImpl.java'('''
                            import org.apache.commons.math3.util.BigReal;
                            
                            public class ToolImpl { public void execute() { BigReal read = BigReal.ONE; } }
                        ''')
                    }
                }
            }
        }

        when:
        succeeds 'a:compileJava'

        then:
        executedAndNotSkipped ':a:compileJava'

        when:
        file('a/build.gradle').text = file('a/build.gradle').text.replace("3.4", "3.4.1")

        then:
        succeeds 'a:compileJava'
        skipped ':a:compileJava'
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
