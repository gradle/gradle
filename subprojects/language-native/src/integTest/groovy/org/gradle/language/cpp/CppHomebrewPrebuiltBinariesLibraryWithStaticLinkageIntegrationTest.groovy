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

import org.gradle.language.nativeplatform.internal.repo.HomebrewBinaryRepository
import org.gradle.nativeplatform.fixtures.app.CppGreeterUsesLogger
import org.gradle.nativeplatform.fixtures.app.SourceElement

class CppHomebrewPrebuiltBinariesLibraryWithStaticLinkageIntegrationTest extends AbstractCppHomebrewPrebuiltBinariesIntegrationTest {
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
                linkage = [Linkage.STATIC]
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
        return [":compileDebugCpp", ":createDebug"]
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugCpp"
    }

    @Override
    protected String getCompileConfigurationName() {
        return "cppCompileDebug"
    }
}
