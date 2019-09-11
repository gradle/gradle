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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.nativeplatform.internal.repo.HomebrewBinaryRepository
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.fixtures.app.AppElement
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.CppGreeterUsesLogger
import org.gradle.nativeplatform.fixtures.app.CppLogger
import org.gradle.nativeplatform.fixtures.app.CppMainUsesGreeter
import org.gradle.nativeplatform.fixtures.app.SourceElement

class CppHomebrewPrebuiltBinariesApplicationIntegrationTest extends AbstractCppHomebrewPrebuiltBinariesIntegrationTest {

    @Override
    protected void makeSingleProject() {
        settingsFile << """
            rootProject.name = "some-thing"
        """
        buildFile << """
            plugins {
                id 'cpp-application'
            } 
            repositories {
                // Would add a factory method of some kind instead of instantiating internal class
                // Base directory would also be a property and have some default value
                def homebrewRepository = objects.newInstance(${HomebrewBinaryRepository.name})
                homebrewRepository.location = file('homebrew')
                add(homebrewRepository)
            }
        """
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "application"
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppAppWithGreeterUsingLogger()
    }

    private static class CppAppWithGreeterUsingLogger extends SourceElement implements AppElement {
        private final greeter = new CppGreeterUsesLogger()
        private final main = new CppMainUsesGreeter(greeter)

        @Override
        String getExpectedOutput() {
            return main.expectedOutput
        }

        @Override
        List<SourceFile> getFiles() {
            return main.files + greeter.files
        }
    }

    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary() {
        return [":compileDebugCpp", ":linkDebug", ":installDebug"]
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugCpp"
    }

    @Override
    protected String getCompileConfigurationName() {
        return "cppCompileDebug"
    }

    def "does not resolve linker library when depending only on headers"() {
        given:
        makeSingleProject()
        makeHomebrewPackage(Linkage.SHARED, Linkage.STATIC)
        buildFile << configureDependency("logger::1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        def failure = fails("assemble")

        then:
        failure.assertHasErrorOutput('"Logger::log(')
        failure.assertHasErrorOutput("Greeter::sayHello() in greeter.o")
        failure.assertHasDescription("Execution failed for task ':linkDebug'")
        failure.assertHasCause("Linker failed while linking ${OperatingSystem.current().getExecutableName('some-thing')}.")
    }

    def "installs copy the homebrew binaries"() {
        given:
        makeSingleProject()
        makeHomebrewPackage(Linkage.SHARED)
        buildFile << configureDependency("logger:logger:1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        succeeds("installDebug")

        then:
        result.assertTasksExecutedAndNotSkipped(":compileDebugCpp", ":linkDebug", ":installDebug")
        def installation = installation("build/install/main/debug")
        installation.assertInstalled()
        installation.exec().out == componentUnderTest.expectedOutput
        installation.assertIncludesLibraries("logger")
    }
}
