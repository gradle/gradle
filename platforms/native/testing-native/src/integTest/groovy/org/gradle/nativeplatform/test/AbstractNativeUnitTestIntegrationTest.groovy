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

package org.gradle.nativeplatform.test

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.LanguageTaskNames
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.junit.Assume

import static org.gradle.nativeplatform.MachineArchitecture.X86
import static org.gradle.nativeplatform.MachineArchitecture.X86_64

abstract class AbstractNativeUnitTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements LanguageTaskNames {
    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestWithApplicationIntegrationTest',
        'SwiftXCTestWithoutComponentIntegrationTest'
    ])
    def "does nothing when no source files are present"() {
        given:
        makeSingleProject()

        when:
        run("check")

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test", ":check")
        result.assertTasksSkipped(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test", ":check")
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestWithApplicationIntegrationTest',
        'SwiftXCTestWithoutComponentIntegrationTest'
    ])
    def "runs tests when #task lifecycle task executes"() {
        given:
        makeSingleProject()
        writeTests()

        when:
        succeeds(task)

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, expectedLifecycleTasks)
        assertTestCasesRan()

        where:
        task    | expectedLifecycleTasks
        "test"  | [":test"]
        "check" | [":test", ":check"]
        "build" | [":test", ":check", ":build", tasksToAssembleComponentUnderTest, ":assemble"]
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32_AND_64)
    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestWithApplicationIntegrationTest',
        'SwiftXCTestWithoutComponentIntegrationTest'
    ])
    def "runs tests when #task lifecycle task executes and target machines are specified on the component under test"() {
        Assume.assumeFalse(componentUnderTestDsl == null)

        given:
        makeSingleProject()
        writeTests()
        configureTargetMachines("machines.os('${currentOsFamilyName}').x86, machines.os('${currentOsFamilyName}').x86_64")

        when:
        succeeds(task)

        then:
        result.assertTasksExecuted(getTasksToCompileComponentUnderTest(expectedArchitecture), getTasksToBuildAndRunUnitTest(expectedArchitecture), expectedLifecycleTasks)
        assertTestCasesRan()

        where:
        task         | expectedArchitecture | expectedLifecycleTasks
        "test"       | X86_64               | [":test"]
        "check"      | X86_64               | [":test", ":check"]
        "build"      | X86_64               | [":test", ":check", ":build", getTasksToAssembleComponentUnderTest(X86_64), ":assemble"]
        "runTestX86" | X86                  | [":runTestX86"]
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32_AND_64)
    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestWithApplicationIntegrationTest',
        'SwiftXCTestWithoutComponentIntegrationTest'
    ])
    def "runs tests when #task lifecycle task executes and target machines are specified on both main component and test component"() {
        Assume.assumeFalse(componentUnderTestDsl == null)

        given:
        makeSingleProject()
        writeTests()
        configureTargetMachines("machines.os('${currentOsFamilyName}').x86, machines.os('${currentOsFamilyName}').x86_64")
        configureTestTargetMachines("machines.os('${currentOsFamilyName}').x86_64")

        when:
        succeeds(task)

        then:
        result.assertTasksExecuted(getTasksToCompileComponentUnderTest(expectedArchitecture), tasksToBuildAndRunUnitTest, expectedLifecycleTasks)
        assertTestCasesRan()

        where:
        task    | expectedArchitecture | expectedLifecycleTasks
        "test"  | X86_64               | [":test"]
        "check" | X86_64               | [":test", ":check"]
        "build" | X86_64               | [":test", ":check", ":build", getTasksToAssembleComponentUnderTest(X86_64), ":assemble"]
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestWithApplicationIntegrationTest',
        'SwiftXCTestWithoutComponentIntegrationTest'
    ])
    def "runs tests when #task lifecycle task executes and target machines are specified on the test component only"() {
        given:
        makeSingleProject()
        writeTests()
        configureTestTargetMachines("machines.os('${currentOsFamilyName}').architecture('${currentArchitecture}')")

        when:
        succeeds(task)

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, expectedLifecycleTasks)
        assertTestCasesRan()

        where:
        task    | expectedLifecycleTasks
        "test"  | [":test"]
        "check" | [":test", ":check"]
        "build" | [":test", ":check", ":build", tasksToAssembleComponentUnderTest, ":assemble"]
    }

    def "fails when target machines are specified on the test component that do not match those on the main component"() {
        Assume.assumeFalse(componentUnderTestDsl == null)

        given:
        makeSingleProject()
        writeTests()
        def otherArchitecture = currentArchitecture == 'x86' ? X86_64 : X86
        configureTargetMachines("machines.os('${currentOsFamilyName}').architecture('${currentArchitecture}')")
        configureTestTargetMachines("machines.os('${currentOsFamilyName}').architecture('${otherArchitecture}')")

        expect:
        fails("test")

        and:
        failure.assertHasCause("The target machine ${currentOsFamilyName}:${otherArchitecture} was specified for the unit test, but this target machine was not specified on the component under test.")
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'SwiftXCTestWithBothLibraryLinkageIntegrationTest',
        'SwiftXCTestWithSharedLibraryLinkageIntegrationTest',
        'SwiftXCTestWithStaticLibraryLinkageIntegrationTest',
        'SwiftXCTestWithApplicationIntegrationTest',
        'SwiftXCTestWithoutComponentIntegrationTest'
    ])
    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        given:
        makeSingleProject()
        writeTests()

        succeeds("test")

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test")
        result.assertTasksSkipped(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test")

        when:
        changeTestImplementation()
        succeeds("test")

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test")
        result.assertTasksSkipped(tasksToCompileComponentUnderTest + tasksToRelocate)
        result.assertTasksNotSkipped(tasksToBuildAndRunUnitTest - tasksToRelocate, ":test")
    }

    private void configureTargetMachines(String targetMachineDeclaration) {
        buildFile << """
            ${componentUnderTestDsl} {
                targetMachines = [${targetMachineDeclaration}]
            }
        """
    }

    private void configureTestTargetMachines(String targetMachineDeclaration) {
        buildFile << """
            ${testComponentDsl} {
                targetMachines = [${targetMachineDeclaration}]
            }
        """
    }

    // Creates a single project build with no source
    protected abstract void makeSingleProject()

    // Writes test source for tests that all pass and main component, if any
    protected abstract void writeTests()

    // Updates the test implementation
    protected abstract void changeTestImplementation()

    // Asserts expected tests ran
    protected abstract void assertTestCasesRan()

    // Gets the dsl extension for the component under test
    protected abstract String getComponentUnderTestDsl()

    // Gets the dsl extension for the test component
    protected abstract String getTestComponentDsl()

    protected abstract String[] getTasksToBuildAndRunUnitTest()

    protected abstract String[] getTasksToBuildAndRunUnitTest(String architecture)

    protected abstract String[] getTasksToRelocate()

    protected String[] getTasksToCompileComponentUnderTest() {
        return []
    }

    protected String[] getTasksToAssembleComponentUnderTest() {
        return []
    }

    protected String[] getTasksToCompileComponentUnderTest(String architecture) {
        return []
    }

    protected String[] getTasksToAssembleComponentUnderTest(String architecture) {
        return []
    }

    protected String[] getTasksToRelocate(String architecture) {
        return []
    }
}
