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

import org.gradle.ide.xcode.fixtures.XcodebuildExecutor
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.CppSourceElement
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

abstract class AbstractXcodeCppProjectIntegrationTest extends AbstractXcodeNativeProjectIntegrationTest {
    @Override
    protected void assertXcodeProjectSources(List<String> rootChildren) {
        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(rootChildren + ['Sources', 'Headers'])
        project.sources.assertHasChildren(componentUnderTest.sources.files*.name)
        project.headers.assertHasChildren(componentUnderTest.headers.files*.name)
    }

    @Override
    protected abstract CppSourceElement getComponentUnderTest()

    @Requires([
        UnitTestPreconditions.HasXCode,
        UnitTestPreconditions.NotMacOsM1
    ]) // TODO KM: Not sure why error message is different on M1
    @ToBeFixedForConfigurationCache
    def "returns meaningful errors from xcode when component product is unbuildable due to architecture"() {
        useXcodebuildTool()

        given:
        makeSingleProject()
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo')")
        buildFile << configureToolChainSupport('foo')

        componentUnderTest.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def result = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .execWithFailure(XcodebuildExecutor.XcodeAction.BUILD)

        then:
        result.out.contains('No tool chain is available to build C++') ||
            result.error.contains('My Mac doesn’t support any of App’s architectures. You can set App’s Architectures build setting to Standard Architectures to support My Mac')
    }

    protected String configureToolChainSupport(String architecture) {
        return """
            model {
                toolChains {
                    toolChainFor${architecture.capitalize()}Architecture(Gcc) {
                        path "/not/found"
                        target("host:${architecture}")
                    }
                }
            }
        """
    }
}
