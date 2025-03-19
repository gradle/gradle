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

import org.codehaus.groovy.control.CompilerConfiguration
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class JavaLibraryCompilationIntegrationTest extends AbstractIntegrationSpec {

    private toggleCompileClasspathPackaging(boolean activate) {
        if (activate) {
            propertiesFile << """
                systemProp.org.gradle.java.compile-classpath-packaging=true
            """.trim()
        }
    }

    def "project can declare an API dependency [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)

        given:
        subproject('a') {
            'build.gradle'('''
                apply plugin: 'java-library'
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
                apply plugin: 'java-library'
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
        packagingTasks(compileClasspathPackaging)

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _
    }

    def "uses the default configuration when producer is not a library"() {
        given:
        subproject('a') {
            'build.gradle'('''
                apply plugin: 'java-library'
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

    def "uses the API of a library when compiling production code against it using the #configuration configuration"() {
        given:
        subproject('a') {
            'build.gradle'("""
                apply plugin: 'java'
                dependencies {
                    implementation project(':b')
                }
            """)
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
                apply plugin: 'java-library'
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

    def "uses the API of a library when compiling a custom source set against it [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)

        given:
        subproject('a') {
            'build.gradle'("""
                apply plugin: 'java'
                sourceSets {
                    foo {
                        java.srcDir 'src/foo/java'
                    }
                }
                dependencies {
                    fooImplementation project(':b')
                }
            """)
            src {
                foo {
                    java {
                        'ToolImpl.java'('public class ToolImpl implements Tool { public void execute() {} }')
                    }
                }
            }
        }

        subproject('b') {
            'build.gradle'('''
                apply plugin: 'java-library'
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
        succeeds 'a:compileFooJava'

        then:
        executedAndNotSkipped ':b:compileJava'
        packagingTasks(compileClasspathPackaging)

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _
    }

    def "uses the API of a library when compiling tests against it using the #configuration configuration"() {
        given:
        subproject('a') {
            'build.gradle'("""
                apply plugin: 'java'
                dependencies {
                    testImplementation project(':b')
                }
            """)
            src {
                test {
                    java {
                        'ToolTest.java'('public class ToolTest { public void test(Tool tool) {} }')
                    }
                }
            }
        }

        subproject('b') {
            'build.gradle'('''
                apply plugin: 'java-library'
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
        succeeds 'a:compileTestJava'

        then:
        executedAndNotSkipped ':b:compileJava'
        notExecuted ':b:processResources', ':b:classes', ':b:jar'
    }

    def "recompiles consumer if API dependency of producer changed [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)

        publishSharedV1()
        publishSharedV11()

        given:
        subproject('a') {
            'build.gradle'("""
                apply plugin: 'java'

                repositories {
                    maven { url = '$mavenRepo.uri' }
                }

                dependencies {
                    implementation project(':b')
                }
            """)
            src {
                main {
                    java {
                        'ToolImpl.java'('public class ToolImpl extends Tool { public void execute() {} }')
                    }
                }
            }
        }

        subproject('b') {
            'build.gradle'("""
                apply plugin: 'java-library'

                repositories {
                    maven { url = '$mavenRepo.uri' }
                }

                dependencies {
                    api 'org.gradle.test:shared:1.0'
                }
            """)
            src {
                main {
                    java {
                        'Tool.java'('public class Tool { public Foo foo() { return new Foo(); } public void execute() {} }')
                    }
                }
            }
        }

        when:
        succeeds 'a:compileJava'

        then:
        executedAndNotSkipped ':a:compileJava', ':b:compileJava'
        packagingTasks(compileClasspathPackaging)

        when:
        file('b/build.gradle').text = file('b/build.gradle').text.replace(/api 'org.gradle.test:shared:1.0'/, '''
            // update an API dependency
            api 'org.gradle.test:shared:1.1'
        ''')

        then:
        succeeds ':a:compileJava', 'a:compileJava'
        executedAndNotSkipped ':b:compileJava'
        packagingTasks(compileClasspathPackaging)

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _
    }

    def "doesn't recompile consumer if implementation dependency of producer changed [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)

        publishSharedV1()
        publishSharedV11()

        given:
        subproject('a') {
            'build.gradle'("""
                apply plugin: 'java'

                dependencies {
                    implementation project(':b')
                }
            """)
            src {
                main {
                    java {
                        'ToolImpl.java'('public class ToolImpl extends Tool { public void execute() {} }')
                    }
                }
            }
        }

        subproject('b') {
            'build.gradle'("""
                apply plugin: 'java-library'

                repositories {
                    maven { url = '$mavenRepo.uri' }
                }

                dependencies {
                    implementation 'org.gradle.test:shared:1.0'
                }
            """)
            src {
                main {
                    java {
                        'Tool.java'('public class Tool { private Foo foo; public void execute() {}; }')
                    }
                }
            }
        }

        when:
        succeeds 'a:compileJava'

        then:
        executedAndNotSkipped ':a:compileJava', ':b:compileJava'
        packagingTasks(compileClasspathPackaging)

        when:
        file('b/build.gradle').text = file('b/build.gradle').text.replace(/implementation 'org.gradle.test:shared:1.0'/, '''
            // update an API dependency
            implementation 'org.gradle.test:shared:1.1'
        ''')

        then:
        succeeds 'a:compileJava'
        executedAndNotSkipped ':b:compileJava'
        packagingTasks(compileClasspathPackaging)
        skipped ':a:compileJava'

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _
    }

    def "can depend on #scenario without building the jar"() {
        given:
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            apply plugin: 'java'

            dependencies {
                implementation project(':b')
            }

            task processDependency {
                def lazyInputs = configurations.runtimeClasspath.incoming.artifactView {
                    attributes{ attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.${token})) }
                }.files
                inputs.files(lazyInputs)
                doLast {
                    assert org.gradle.util.internal.CollectionUtils.single(lazyInputs.files).toPath().endsWith('${expectedDirName}')
                }
            }
        """
        file('b/build.gradle') << '''
            apply plugin: 'java-library'
        '''
        file('b/src/main/java/Foo.java') << 'class Foo {}'
        file('b/src/main/resources/foo.txt') << 'some resource'

        when:
        run 'processDependency'

        then:
        executedAndNotSkipped ":b:$executed"
        notExecuted ":b:$notExec"

        where:
        scenario              | token       | expectedDirName     | executed           | notExec
        'class directory'     | 'CLASSES'   | 'classes/java/main' | 'compileJava'      | 'processResources'
        'resources directory' | 'RESOURCES' | 'resources/main'    | 'processResources' | 'compileJava'
    }

    private void packagingTasks(boolean expectExecuted) {
        def tasks = [':b:processResources', ':b:classes', ':b:jar']
        if (expectExecuted) {
            executed(*tasks)
        } else {
            notExecuted(*tasks)
        }
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

    private void publishSharedV1() {
        buildSharedJar('1.0', 'class Foo {}')
    }

    private void publishSharedV11() {
        buildSharedJar('1.1', 'class Foo { int x }')
    }

    private void buildSharedJar(String version, String classBody) {
        def jar = mavenRepo.module('org.gradle.test', 'shared', version).publish().artifactFile
        def configuration = new CompilerConfiguration()
        def bytes = null
        configuration.setBytecodePostprocessor { String name, byte[] original ->
            bytes = original
        }
        def gcl = new GroovyClassLoader(this.class.classLoader, configuration)
        gcl.parseClass(classBody)
        def jos = new JarOutputStream(jar.newOutputStream())
        jos.putNextEntry(new JarEntry("Foo.class"))
        jos.write(bytes)
        jos.closeEntry()
        jos.close()
        jar
    }
}
