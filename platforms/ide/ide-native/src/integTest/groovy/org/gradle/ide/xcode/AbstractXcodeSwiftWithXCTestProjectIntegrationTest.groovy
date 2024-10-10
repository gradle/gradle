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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.fixtures.app.Swift3WithSwift4XCTest
import org.gradle.nativeplatform.fixtures.app.Swift3WithXCTest
import org.gradle.nativeplatform.fixtures.app.Swift4WithSwift3XCTest
import org.gradle.nativeplatform.fixtures.app.Swift4WithXCTest
import org.gradle.nativeplatform.fixtures.app.Swift5WithSwift4XCTest
import org.gradle.nativeplatform.fixtures.app.Swift5WithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftSourceElement

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
    SwiftSourceElement getSwift5Component() {
        return new Swift5WithXCTest(rootProjectName)
    }

    @Override
    String getComponentUnderTestDsl() {
        return "xctest"
    }

    abstract String getTestedComponentDsl()

    @Override
    protected String configureTargetMachines(String... targetMachines) {
        return """
            ${testedComponentDsl}.targetMachines = [${targetMachines.join(",")}]
        """ + super.configureTargetMachines(targetMachines)
    }

    @Override
    protected void assertXcodeProjectSources(List<String> rootChildren) {
        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(rootChildren + ['Sources', 'Tests'])
        project.sources.assertHasChildren(componentUnderTest.main.files*.name)
        project.tests.assertHasChildren(componentUnderTest.test.files*.name)
    }

    @Override
    protected List<ExpectedXcodeTarget> getExpectedXcodeTargets() {
        return super.getExpectedXcodeTargets() + [new ExpectedXcodeTarget('AppTest')]
    }

    @ToBeFixedForConfigurationCache
    def "honors Swift source compatibility difference on both tested component (#componentSourceCompatibility) and XCTest component (#xctestSourceCompatibility)"() {
        given:
        // TODO: Generating the Xcode files for incompatible source compatibility shouldn't fail the build
        //   Thus, we should be able to remove the assumption below.
        assumeSwiftCompilerSupportsLanguageVersion(componentSourceCompatibility)
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
        assertHasSwiftVersion(componentSourceCompatibility, rootXcodeProject.projectFile.findTargets(fixture.main.moduleName))
        assertHasSwiftVersion(xctestSourceCompatibility, rootXcodeProject.projectFile.findTargets(fixture.test.moduleName))

        where:
        fixture                                     | componentSourceCompatibility | xctestSourceCompatibility
        new Swift3WithSwift4XCTest(rootProjectName) | SwiftVersion.SWIFT3          | SwiftVersion.SWIFT4
        new Swift4WithSwift3XCTest(rootProjectName) | SwiftVersion.SWIFT4          | SwiftVersion.SWIFT3
        new Swift5WithSwift4XCTest(rootProjectName) | SwiftVersion.SWIFT5          | SwiftVersion.SWIFT4
    }
}
