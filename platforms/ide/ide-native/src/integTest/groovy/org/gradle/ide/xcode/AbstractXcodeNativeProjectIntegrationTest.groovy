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

package org.gradle.ide.xcode

import org.gradle.ide.xcode.fixtures.AbstractXcodeIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.test.precondition.Requires

import static org.gradle.test.preconditions.IntegTestPreconditions.NotEmbeddedExecutor
import static org.gradle.test.preconditions.UnitTestPreconditions.HasXCode

abstract class AbstractXcodeNativeProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "can create xcode project for unbuildable component"() {
        given:
        makeSingleProject()
        buildFile << configureTargetMachines("machines.os('os-family')")
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        assertXcodeProjectSources(['build.gradle'])

        def project = rootXcodeProject.projectFile
        project.targets.size() == expectedXcodeTargets.size()
        project.targets.eachWithIndex { target, idx ->
            assert target.productReference == null
            assert target.buildArgumentsString == null

            target.assertIsIndexer()
            assert target.name == "[INDEXING ONLY] ${expectedXcodeTargets[idx].name}"
            target.assertProductNameEquals(expectedXcodeTargets[idx].name)
            assert target.buildConfigurationList.buildConfigurations.size() == 1

            assert target.buildConfigurationList.buildConfigurations[0].name == "unbuildable"
            assert target.buildConfigurationList.buildConfigurations[0].buildSettings.ARCHS == null
            assert target.buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == null
            assert target.buildConfigurationList.buildConfigurations[0].buildSettings.VALID_ARCHS == null
        }
    }

    @ToBeFixedForConfigurationCache
    def "warns about unbuildable components in generated xcode project"() {
        given:
        makeSingleProject()
        buildFile << configureTargetMachines("machines.os('os-family')")
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        expectedXcodeTargets.each { target ->
            result.assertOutputContains("'${target.name}' component in project ':' is not buildable.");
        }
    }

    @Requires(value = [HasXCode, NotEmbeddedExecutor], reason = "Need a Gradle install to pass to xcodebuild")
    @ToBeFixedForConfigurationCache
    def "returns meaningful errors from xcode when component product is unbuildable due to operating system"() {
        useXcodebuildTool()

        given:
        makeSingleProject()
        buildFile << configureTargetMachines("machines.os('os-family')")

        componentUnderTest.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def result = xcodebuild
                .withProject(rootXcodeProject)
                .withScheme("App")
                .fails()

        then:
        result.error.contains('The project named "app" does not contain a scheme named "App".')
    }

    protected void configureComponentUnderTest(String buildScript) {
        buildFile << """
            ${componentUnderTestDsl} {
                $buildScript
            }
        """
    }

    protected String configureTargetMachines(String... targetMachines) {
        return """
            ${componentUnderTestDsl}.targetMachines = [${targetMachines.join(",")}]
        """
    }

    protected abstract void makeSingleProject()

    protected abstract String getComponentUnderTestDsl()

    protected abstract SourceElement getComponentUnderTest()

    protected abstract void assertXcodeProjectSources(List<String> rootChildren)

    protected List<ExpectedXcodeTarget> getExpectedXcodeTargets() {
        return [new ExpectedXcodeTarget('App')]
    }

    protected static class ExpectedXcodeTarget {
        final String name

        ExpectedXcodeTarget(String name) {
            this.name = name
        }
    }


}
