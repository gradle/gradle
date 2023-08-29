/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.devel.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.archive.JarTestFixture
import spock.lang.IgnoreIf
import spock.lang.Issue

class JavaGradlePluginPluginIntegrationTest extends WellBehavedPluginTest {
    final static String NO_DESCRIPTOR_WARNING = JavaGradlePluginPlugin.NO_DESCRIPTOR_WARNING_MESSAGE.substring(4)
    final static String DECLARED_PLUGIN_MISSING_MESSAGE = JavaGradlePluginPlugin.DECLARED_PLUGIN_MISSING_MESSAGE
    final static String BAD_IMPL_CLASS_WARNING_PREFIX = JavaGradlePluginPlugin.BAD_IMPL_CLASS_WARNING_MESSAGE.substring(4).split('[%]')[0]
    final static String INVALID_DESCRIPTOR_WARNING_PREFIX = JavaGradlePluginPlugin.INVALID_DESCRIPTOR_WARNING_MESSAGE.substring(4).split('[%]')[0]

    @Override
    String getMainTask() {
        return "jar"
    }

    def "applying java-gradle-plugin causes project to be a java project"() {
        given:
        applyPlugin()

        expect:
        succeeds "compileJava"
    }

    def "jar produces usable plugin jar"() {
        given:
        buildFile()
        def descriptorFile = goodPluginDescriptor()
        goodPlugin()

        expect:
        succeeds "jar"
        def jar = new JarTestFixture(file('build/libs/test.jar'))
        jar.assertContainsFile('META-INF/gradle-plugins/test-plugin.properties') &&
            jar.assertFileContent('META-INF/gradle-plugins/test-plugin.properties', descriptorFile.text)
        jar.assertContainsFile('com/xxx/TestPlugin.class')
        ! output.contains(NO_DESCRIPTOR_WARNING)
        ! output.contains(BAD_IMPL_CLASS_WARNING_PREFIX)
        ! output.contains(INVALID_DESCRIPTOR_WARNING_PREFIX)
    }

    def "jar issues warning if built jar does not contain any plugin descriptors" () {
        given:
        buildFile()
        goodPlugin()

        expect:
        succeeds "jar"
        output.contains(NO_DESCRIPTOR_WARNING)
    }

    def "jar issues warning if built jar does not contain declared plugin" () {
        given:
        buildFile()
        goodPlugin()
        goodPluginDescriptor()
        buildFile << """
            gradlePlugin {
                plugins {
                    otherPlugin {
                        id = 'other-plugin'
                        implementationClass = 'com.xxx.OtherPlugin'
                    }
                }
            }
            pluginDescriptors.enabled = false
        """

        expect:
        succeeds "jar"
        output.contains(String.format(':jar', DECLARED_PLUGIN_MISSING_MESSAGE, "otherPlugin", "other-plugin"))
    }

    def "jar issues warning if built jar contains bad descriptor" (String descriptorContents, String warningMessage) {
        given:
        buildFile()
        badPluginDescriptor(descriptorContents)
        goodPlugin()

        expect:
        succeeds "jar"
        output.contains(warningMessage)

        where:
        descriptorContents                              | warningMessage
        ''                                              | NO_DESCRIPTOR_WARNING
        'implementation-class='                         | INVALID_DESCRIPTOR_WARNING_PREFIX
        'implementation-class=com.xxx.WrongPluginClass' | BAD_IMPL_CLASS_WARNING_PREFIX
    }

    def "jar issues warning if built jar contains one bad descriptor out of multiple descriptors" (String descriptorContents, String warningMessage) {
        given:
        buildFile()
        goodPluginDescriptor()
        badPluginDescriptor('bad-plugin', descriptorContents)
        goodPlugin()

        expect:
        succeeds "jar"
        output.count(warningMessage) == 1

        where:
        descriptorContents                              | warningMessage
        'implementation-class='                         | INVALID_DESCRIPTOR_WARNING_PREFIX
        'implementation-class=com.xxx.WrongPluginClass' | BAD_IMPL_CLASS_WARNING_PREFIX
    }

    def "jar issues correct warnings if built jar contains multiple bad descriptors" (String descriptorContents, String warningMessage, int messageCount) {
        given:
        buildFile()
        badPluginDescriptor('bad-plugin1', descriptorContents)
        badPluginDescriptor('bad-plugin2', descriptorContents)
        goodPlugin()

        expect:
        succeeds "jar"
        output.count(warningMessage) == messageCount

        where:
        descriptorContents                              | warningMessage                    | messageCount
        ''                                              | NO_DESCRIPTOR_WARNING             | 1
        'implementation-class='                         | INVALID_DESCRIPTOR_WARNING_PREFIX | 2
        'implementation-class=com.xxx.WrongPluginClass' | BAD_IMPL_CLASS_WARNING_PREFIX     | 2
    }

    def "jar issues correct warnings if built jar contains mixed descriptor problems" () {
        given:
        buildFile()
        badPluginDescriptor('bad-plugin1', 'implementation-class=')
        badPluginDescriptor('bad-plugin2', 'implementation-class=com.xxx.WrongPluginClass')
        goodPlugin()

        expect:
        succeeds "jar"
        output.count(BAD_IMPL_CLASS_WARNING_PREFIX) == 1
        output.count(INVALID_DESCRIPTOR_WARNING_PREFIX) == 1
    }

    def "Fails if plugin declaration has no id"() {
        given:
        buildFile()
        buildFile << """
            gradlePlugin {
                plugins {
                    helloPlugin {
                        implementationClass = 'com.foo.Bar'
                    }
                }
            }
        """
        expect:
        fails "jar"
        failureCauseContains(String.format(JavaGradlePluginPlugin.DECLARATION_MISSING_ID_MESSAGE, 'helloPlugin'))
    }

    def "Fails if plugin declaration has no implementation class"() {
        given:
        buildFile()
        buildFile << """
            gradlePlugin {
                plugins {
                    helloPlugin {
                        id = 'hello'
                    }
                }
            }
        """
        expect:
        fails "jar"
        failureCauseContains(String.format(JavaGradlePluginPlugin.DECLARATION_MISSING_IMPLEMENTATION_MESSAGE, 'helloPlugin'))
    }

    def "Generates plugin descriptor for declared plugin"() {
        given:
        buildFile()
        goodPlugin()
        buildFile << """
            gradlePlugin {
                plugins {
                    testPlugin {
                        id = 'test-plugin'
                        implementationClass = 'com.xxx.TestPlugin'
                    }
                }
            }
        """
        expect:
        succeeds "jar"
    }

    def "Generated plugin descriptor are os independent"() {
        given:
        buildFile()
        goodPlugin()
        buildFile << """
            gradlePlugin {
                plugins {
                    testPlugin {
                        id = 'test-plugin'
                        implementationClass = 'com.xxx.TestPlugin'
                    }
                }
            }
        """
        expect:
        succeeds "jar"
        def descriptorHash = file("build/resources/main/META-INF/gradle-plugins/test-plugin.properties").md5Hash
        descriptorHash == "0698dda8ffafedc3b054c5fe3aae95f1"
    }

    def "Plugin descriptor generation is up-to-date if declarations did not change"() {
        given:
        buildFile()
        goodPlugin()
        buildFile << """
            gradlePlugin {
                plugins {
                    testPlugin {
                        id = 'test-plugin'
                        implementationClass = 'com.xxx.TestPlugin'
                    }
                }
            }
        """
        expect:
        succeeds "jar"
        succeeds "jar"
        skipped ":pluginDescriptors"
    }

    def "Plugin descriptor generation clears output folder"() {
        given:
        buildFile()
        goodPlugin()
        buildFile << """
            gradlePlugin {
                plugins {
                    testPlugin {
                        id = 'test-plugin'
                        implementationClass = 'com.xxx.TestPlugin'
                    }
                }
            }
        """
        succeeds "jar"
        buildFile << """
            gradlePlugin.plugins.testPlugin.id = 'test-plugin-changed'
        """
        expect:

        succeeds "jar"
        file("build/pluginDescriptors").listFiles().size() == 1
        file("build/resources/main/META-INF/gradle-plugins").listFiles().size() == 1
    }

    @Issue("https://github.com/gradle/gradle/issues/1061")
    def "one plugin project can depend on another when using publishing plugins"() {
        given:
        settingsFile << "include 'a', 'b'"
        file("a/build.gradle") << """
            apply plugin: 'java-gradle-plugin'
            apply plugin: 'maven-publish'
            apply plugin: 'ivy-publish'
            gradlePlugin {
                plugins {
                    foo {
                        id = 'foo-plugin'
                        implementationClass = "com.foo.Foo"
                    }
                }
            }
        """
        file("b/build.gradle") << """
            apply plugin: 'java-gradle-plugin'
            apply plugin: 'maven-publish'
            apply plugin: 'ivy-publish'
            dependencies {
                implementation project(':a')
            }
        """
        expect:
        succeeds("assemble")
    }

    @IgnoreIf({ GradleContextualExecuter.embedded }) // ProjectBuilder needs full distribution
    @Issue("https://github.com/gradle/gradle/issues/18647")
    def "can test plugin with ProjectBuilder without warnings or errors"() {
        given:
        applyPlugin()
        goodPluginDescriptor()
        buildFile << """
            ${mavenCentralRepository()}
            testing.suites.test.useJUnit()
        """
        goodPlugin()
        file("src/test/java/com/xxx/TestPluginTest.java") << """
            import org.junit.Test;
            import org.gradle.api.Project;
            import org.gradle.testfixtures.ProjectBuilder;
            public class TestPluginTest {
                @Test
                public void test() {
                    Project project = ProjectBuilder.builder().build();
                    project.getPlugins().apply("test-plugin");
                }
            }
        """

        when:
        executer.withJdkWarningChecksEnabled()

        then:
        succeeds "test"
    }

    @Issue("https://github.com/gradle/gradle/issues/25732")
    def "can set tags with Groovy = assignment"() {
        given:
        buildFile()
        goodPlugin()
        buildFile << """
            gradlePlugin {
                plugins {
                    testPlugin {
                        id = 'test-plugin'
                        implementationClass = 'com.xxx.TestPlugin'
                        tags = ["first", "second", "third"]
                    }
                }
            }

            gradlePlugin.plugins.all {
                assert it.tags.get() == ["first", "second", "third"] as Set<String>
            }
        """

        expect:
        succeeds "jar"
    }

    def buildFile() {
        buildFile << """
apply plugin: 'java-gradle-plugin'

jar {
    archiveFileName = 'test.jar'
}
"""
    }

    def goodPluginDescriptor() {
        file('src/main/resources/META-INF/gradle-plugins/test-plugin.properties') << """
implementation-class=com.xxx.TestPlugin
"""
    }

    def goodPlugin() {
        file('src/main/java/com/xxx/TestPlugin.java') << """
package com.xxx;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
public class TestPlugin implements Plugin<Project> {
    public void apply(Project project) { }
}
"""
    }

    def badPluginDescriptor(String descriptorId, String descriptorContents) {
        file("src/main/resources/META-INF/gradle-plugins/${descriptorId}.properties") << descriptorContents
    }

    def badPluginDescriptor(String descriptorContents) {
        badPluginDescriptor('test-plugin', descriptorContents)
    }
}
