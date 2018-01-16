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
import org.gradle.ide.xcode.fixtures.ProjectFile
import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.fixtures.app.Swift3
import org.gradle.nativeplatform.fixtures.app.Swift4
import org.gradle.nativeplatform.fixtures.app.SwiftSourceElement
import spock.lang.Unroll

abstract class AbstractXcodeSwiftProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        requireSwiftToolChain()
    }

    @Unroll
    def "detect Swift source compatibility from selected Swift #sourceCompatibility compiler"() {
        assumeSwiftCompilerVersion(sourceCompatibility)

        given:
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        makeSingleProject()

        fixture.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        def targets = rootXcodeProject.projectFile.findTargets(fixture.moduleName)
        assertHasSwiftVersion(sourceCompatibility, targets)

        where:
        fixture         | sourceCompatibility
        swift3Component | SwiftVersion.SWIFT3
        swift4Component | SwiftVersion.SWIFT4
    }

    @Unroll
    def "take specified Swift source compatibility (#sourceCompatibility) regardless of the selected Swift compiler"() {
        given:
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        makeSingleProject()
        buildFile << """
            ${componentUnderTestDsl}.sourceCompatibility = SwiftVersion.${sourceCompatibility.name()}
        """

        fixture.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        def targets = rootXcodeProject.projectFile.findTargets(fixture.moduleName)
        assertHasSwiftVersion(sourceCompatibility, targets)

        where:
        fixture         | sourceCompatibility
        swift3Component | SwiftVersion.SWIFT3
        swift4Component | SwiftVersion.SWIFT4
    }

    abstract void makeSingleProject()

    SwiftSourceElement getSwift3Component() {
        return new Swift3(rootProjectName)
    }

    SwiftSourceElement getSwift4Component() {
        return new Swift4(rootProjectName)
    }

    abstract String getComponentUnderTestDsl()

    void assertHasSwiftVersion(SwiftVersion expectedSwiftVersion, List<ProjectFile.PBXTarget> targets) {
        assert !targets.empty
        targets.each { target ->
            def buildConfigurations = target.buildConfigurationList.buildConfigurations
            assert !buildConfigurations.empty
            buildConfigurations.each { buildConfiguration ->
                assert buildConfiguration.buildSettings.SWIFT_VERSION == "${expectedSwiftVersion.version}.0"
            }
        }
    }
}
