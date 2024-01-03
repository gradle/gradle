/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class EclipseJavaProjectIntegrationTest extends AbstractEclipseIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "generates default JRE container paths recognized by Eclipse"(String version, String expectedContainer) {
        settingsFile << "rootProject.name = 'java'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'
            java.targetCompatibility = $version
        """

        when:
        run "eclipse"

        then:
        def classpath = classpath
        classpath.containers == [ "org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/$expectedContainer/" ]

        where:
        version | expectedContainer
        '1.1'   | 'JRE-1.1'
        '1.2'   | 'J2SE-1.2'
        '1.3'   | 'J2SE-1.3'
        '1.4'   | 'J2SE-1.4'
        '1.5'   | 'J2SE-1.5'
        '1.6'   | 'JavaSE-1.6'
        '1.7'   | 'JavaSE-1.7'
        '1.8'   | 'JavaSE-1.8'
        '1.9'   | 'JavaSE-9'
        '1.10'  | 'JavaSE-10'
    }

    @ToBeFixedForConfigurationCache
    def "generated JDT preferences have correct compiler version"(String version, String expectedVersion) {
        settingsFile << "rootProject.name = 'java'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'
            java.sourceCompatibility = $version
        """

        when:
        run "eclipse"

        then:
        def properties = parseProperties('.settings/org.eclipse.jdt.core.prefs')
        properties['org.eclipse.jdt.core.compiler.compliance'] == expectedVersion
        properties['org.eclipse.jdt.core.compiler.source'] == expectedVersion

        where:
        version | expectedVersion
        '1.1'   | '1.1'
        '1.2'   | '1.2'
        '1.3'   | '1.3'
        '1.4'   | '1.4'
        '1.5'   | '1.5'
        '1.6'   | '1.6'
        '1.7'   | '1.7'
        '1.8'   | '1.8'
        '1.9'   | '9'
        '1.10'  | '10'
    }

    @ToBeFixedForConfigurationCache
    def "generated JDT preferences have correct target platform version"(String version, String expectedVersion) {
        settingsFile << "rootProject.name = 'java'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'
            java.targetCompatibility = $version
        """

        when:
        run "eclipse"

        then:
        def properties = parseProperties('.settings/org.eclipse.jdt.core.prefs')
        properties['org.eclipse.jdt.core.compiler.codegen.targetPlatform'] == expectedVersion

        where:
        version | expectedVersion
        '1.1'   | '1.1'
        '1.2'   | '1.2'
        '1.3'   | '1.3'
        '1.4'   | '1.4'
        '1.5'   | '1.5'
        '1.6'   | '1.6'
        '1.7'   | '1.7'
        '1.8'   | '1.8'
        '1.9'   | '9'
        '1.10'  | '10'
    }

    protected parseProperties(String filename) {
        Properties properties = new Properties()
        File propertiesFile = file(filename)
        propertiesFile.withInputStream { properties.load(it) }
        properties
    }
}
