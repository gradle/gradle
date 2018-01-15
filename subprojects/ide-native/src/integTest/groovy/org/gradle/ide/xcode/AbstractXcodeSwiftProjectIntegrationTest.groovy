/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.xcode

import org.gradle.ide.xcode.fixtures.AbstractXcodeIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.Swift3
import org.gradle.nativeplatform.fixtures.app.Swift4
import org.gradle.nativeplatform.fixtures.app.SwiftSourceElement
import spock.lang.Unroll

abstract class AbstractXcodeSwiftProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        requireSwiftToolChain()
    }

    @Unroll
    def "detect Swift source compatibility from selected Swift #swiftcMajorVersion compiler"() {
        assumeSwiftCompilerVersion(swiftcMajorVersion)

        given:
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        makeSingleProject()

        fixture.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.targets.findAll { it.name.contains(fixture.moduleName) }.buildConfigurationList.buildConfigurations.flatten().each {
            assert it.buildSettings.SWIFT_VERSION == expectedSwiftVersion
        }

        where:
        fixture         | swiftcMajorVersion | expectedSwiftVersion
        swift3Component | 3                  | '3.0'
        swift4Component | 4                  | '4.0'
    }

    @Unroll
    def "take specified Swift source compatibility (#sourceCompatibility) regardless of the selected Swift compiler"() {
        given:
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.sourceCompatibility = ${sourceCompatibility}
        """

        fixture.writeToProject(testDirectory)
        println testDirectory

        when:
        succeeds("xcode")

        then:
        // TODO: select target for specific component (compile and index)
        rootXcodeProject.projectFile.targets.findAll { it.name.contains(fixture.moduleName) }.buildConfigurationList.buildConfigurations.flatten().each {
            assert it.buildSettings.SWIFT_VERSION == expectedSwiftVersion
        }

        where:
        fixture         | sourceCompatibility   | expectedSwiftVersion
        swift3Component | 'SwiftVersion.SWIFT3' | '3.0'
        swift4Component | 'SwiftVersion.SWIFT4' | '4.0'
    }

    abstract void makeSingleProject()

    SwiftSourceElement getSwift3Component() {
        return new Swift3(rootProjectName)
    }

    SwiftSourceElement getSwift4Component() {
        return new Swift4(rootProjectName)
    }

    abstract String getComponentUnderTestDsl()
}
