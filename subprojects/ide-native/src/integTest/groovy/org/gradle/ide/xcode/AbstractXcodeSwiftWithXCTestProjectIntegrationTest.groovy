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

import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.fixtures.app.Swift3WithSwift4XCTest
import org.gradle.nativeplatform.fixtures.app.Swift3WithXCTest
import org.gradle.nativeplatform.fixtures.app.Swift4WithSwift3XCTest
import org.gradle.nativeplatform.fixtures.app.Swift4WithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftSourceElement
import spock.lang.Unroll

abstract class AbstractXcodeSwiftWithXCTestProjectIntegrationTest extends AbstractXcodeSwiftProjectIntegrationTest {
    @Override
    SwiftSourceElement getSwift3Component() {
        return new Swift3WithXCTest(rootProjectName)
    }

    @Override
    SwiftSourceElement getSwift4Component() {
        return new Swift4WithXCTest(rootProjectName)
    }

    @Override
    String getComponentUnderTestDsl() {
        return "xctest"
    }

    abstract String getTestedComponentDsl()

    @Unroll
    def "honors Swift source compatibility difference on both tested component (#componentSourceCompatibility) and XCTest component (#xctestSourceCompatibility)"() {
        given:
        fixture.writeToProject(testDirectory)
        makeSingleProject()
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        buildFile << """
            ${testedComponentDsl}.sourceCompatibility = SwiftVersion.${componentSourceCompatibility.name()}
            xctest.sourceCompatibility = SwiftVersion.${xctestSourceCompatibility.name()}
        """

        when:
        succeeds 'xcode'

        then:
        def targets = rootXcodeProject.projectFile.targets
        targets.findAll { it.name == fixture.main.moduleName }.buildConfigurationList.buildConfigurations.flatten().each {
            assert it.buildSettings.SWIFT_VERSION == "${componentSourceCompatibility.version}.0"
        }
        targets.findAll { it.name == fixture.test.moduleName }.buildConfigurationList.buildConfigurations.flatten().each {
            assert it.buildSettings.SWIFT_VERSION == "${xctestSourceCompatibility.version}.0"
        }

        where:
        fixture                                     | componentSourceCompatibility | xctestSourceCompatibility
        new Swift3WithSwift4XCTest(rootProjectName) | SwiftVersion.SWIFT3          | SwiftVersion.SWIFT4
        new Swift4WithSwift3XCTest(rootProjectName) | SwiftVersion.SWIFT4          | SwiftVersion.SWIFT3
    }
}
