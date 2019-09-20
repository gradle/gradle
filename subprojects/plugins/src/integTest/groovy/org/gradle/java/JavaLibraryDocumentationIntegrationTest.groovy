/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.test.fixtures.archive.ZipTestFixture

class JavaLibraryDocumentationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        subproject('a') {
            'build.gradle'('''
                apply plugin: 'java-library'
                configurations {
                    javadoc {
                        canBeResolved = true; canBeConsumed = false
                        extendsFrom compileClasspath
                        attributes {
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.JAVADOC))
                        }
                    }
                    sources {
                        canBeResolved = true; canBeConsumed = false
                        extendsFrom compileClasspath
                        attributes {
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
                        }
                    }
                }
                
                dependencies { // also get my own!
                    javadoc project(':a')
                    sources project(':a')
                }
                
                dependencies {
                    api project(':b')
                }
                
                task collectJavadoc(type: Copy) {
                    from configurations.javadoc
                    into "$buildDir/javadocs"
                }
                task collectSources(type: Copy) {
                    from configurations.sources
                    into "$buildDir/sources"
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
                dependencies {
                    implementation project(':c')
                }
            ''')
            src {
                main {
                    java {
                        'Tool.java'('public interface Tool { void execute(); }')
                        'ToolHelper.java'('public class ToolHelper { void execute() { new Internal().use(); } ; }')
                    }
                }
            }
        }
        subproject('c') {
            'build.gradle'('''
                apply plugin: 'java-library'
            ''')
            src {
                main {
                    java {
                        'Internal.java'('public class Internal { void use() { } }')
                    }
                }
            }
        }
    }

    def "project packages own and runtime dependency sources if requested"() {
        when:
        succeeds 'a:collectSources'

        then:
        result.assertTasksExecuted(':a:collectSources', ':a:sourcesJar', ':b:sourcesJar', ':c:sourcesJar')
        output('sources') == ['a-sources.jar', 'b-sources.jar', 'c-sources.jar'] as Set
        jar('sources/a-sources.jar').assertContainsFile('ToolImpl.java')
        jar('sources/b-sources.jar').assertContainsFile('Tool.java')
        jar('sources/b-sources.jar').assertContainsFile('ToolHelper.java')
        jar('sources/c-sources.jar').assertContainsFile('Internal.java')
    }

    def "project generates and packages own and api dependency javadoc if requested"() {
        when:
        succeeds 'a:collectJavadoc'

        then:
        // requires 'c' to do compileJava for the classpath of the javadoc task of 'b'
        result.assertTasksExecuted(':a:collectJavadoc', ':a:javadoc', ':a:javadocJar', ':a:classes', ':a:compileJava', ':a:processResources', ':b:javadoc', ':b:javadocJar', ':b:classes', ':b:compileJava', ':b:processResources', ':c:compileJava')
        output('javadocs') == ['a-javadoc.jar', 'b-javadoc.jar'] as Set
        jar('javadocs/a-javadoc.jar').assertContainsFile('ToolImpl.html')
        jar('javadocs/b-javadoc.jar').assertContainsFile('Tool.html')
        jar('javadocs/b-javadoc.jar').assertContainsFile('ToolHelper.html')
    }

    private Set<String> output(String docsType) {
        file("a/build/$docsType").listFiles().collect { it.name } as Set
    }

    private ZipTestFixture jar(String jarName) {
        new ZipTestFixture(file("a/build/$jarName"))
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
