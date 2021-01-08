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

package org.gradle.nativeplatform.test.cpp

import org.gradle.nativeplatform.fixtures.app.CppLibWithSimpleUnitTest
import org.gradle.nativeplatform.fixtures.app.SourceElement

class CppUnitTestComponentWithBothLibraryLinkageIntegrationTest extends AbstractCppUnitTestComponentWithTestedComponentIntegrationTest {
    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'cpp-unit-test'
            library.linkage = [Linkage.SHARED, Linkage.STATIC]
        """
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppLibWithSimpleUnitTest()
    }

    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebugShared${variant.capitalize()}Cpp", ":compileTest${variant.capitalize()}Cpp", ":linkTest${variant.capitalize()}", ":installTest${variant.capitalize()}", ":runTest${variant.capitalize()}"]
    }

    @Override
    String getTestedComponentDsl() {
        return "library"
    }
}
