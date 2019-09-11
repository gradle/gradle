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

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.nativeplatform.internal.repo.HomebrewBinaryRepository
import org.gradle.nativeplatform.fixtures.app.CppGreeterUsesLogger
import org.gradle.nativeplatform.fixtures.app.SourceElement

class CppHomebrewPrebuiltBinariesLibraryWithBothLinkageIntegrationTest extends AbstractCppHomebrewPrebuiltBinariesIntegrationTest {
    @Override
    protected void makeSingleProject() {
        settingsFile << """
            rootProject.name = "some-thing"
        """
        buildFile << """
            plugins {
                id 'cpp-library'
            }
            library {
                linkage = [Linkage.SHARED, Linkage.STATIC]
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
        return "library"
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppGreeterUsesLogger()
    }

    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary() {
        return [":compileDebugSharedCpp", ":linkDebugShared"]
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugSharedCpp"
    }

    @Override
    protected String getCompileConfigurationName() {
        return "cppCompileDebugShared"
    }

    def "does not resolve linker library when depending only on headers"() {
        given:
        makeSingleProject()
        makeHomebrewPackage()
        buildFile << configureDependency("logger::1.2")
        componentUnderTest.writeToProject(testDirectory)

        when:
        def failure = fails("assemble")

        then:
        failure.assertHasErrorOutput('"Logger::log(')
        failure.assertHasErrorOutput("Greeter::sayHello() in greeter.o")
        failure.assertHasDescription("Execution failed for task ':linkDebugShared'")
        failure.assertHasCause("Linker failed while linking ${OperatingSystem.current().getSharedLibraryName('some-thing')}.")
    }
}
