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

package org.gradle.api.plugins.devel

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.test.fixtures.archive.JarTestFixture
import spock.lang.Ignore

class JavaGradlePluginPluginIntegrationTest extends WellBehavedPluginTest {
    final static String NO_DESCRIPTOR_WARNING = JavaGradlePluginPlugin.NO_DESCRIPTOR_WARNING_MESSAGE
    final static String BAD_DESCRIPTOR_WARNING_PREFIX = JavaGradlePluginPlugin.BAD_DESCRIPTOR_WARNING_MESSAGE.split('%')[0]


    @Override
    String getPluginId() {
        "java-gradle-plugin"
    }

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
        goodPluginDescriptor()
        goodPlugin()

        expect:
        succeeds "jar"
        def jar = new JarTestFixture(file('build/libs/test.jar'))
        jar.assertContainsFile('META-INF/gradle-plugins/test-plugin.properties')
        jar.assertContainsFile('com/xxx/TestPlugin.class')
        ! output.contains(NO_DESCRIPTOR_WARNING)
        ! output.contains(BAD_DESCRIPTOR_WARNING_PREFIX)
    }

    def "jar issues warning if built jar does not contain any plugin descriptors" () {
        given:
        buildFile()
        goodPlugin()

        expect:
        succeeds "jar"
        output.contains(NO_DESCRIPTOR_WARNING)
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
        'implementation-class='                         | NO_DESCRIPTOR_WARNING
        'implementation-class=com.xxx.WrongPluginClass' | BAD_DESCRIPTOR_WARNING_PREFIX
    }

    @Ignore
    def buildFile() {
        buildFile << """
apply plugin: 'java-gradle-plugin'

jar {
    archiveName 'test.jar'
}
"""
    }

    @Ignore
    def goodPluginDescriptor() {
        file('src/main/resources/META-INF/gradle-plugins/test-plugin.properties') << """
implementation-class=com.xxx.TestPlugin
"""
    }

    @Ignore
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

    @Ignore
    def badPluginDescriptor(String descriptorContents) {
        file('src/main/resources/META-INF/gradle-plugins/test-plugin.properties') << descriptorContents
    }
}
