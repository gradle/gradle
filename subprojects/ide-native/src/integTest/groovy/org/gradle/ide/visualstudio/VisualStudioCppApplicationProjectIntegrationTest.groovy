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

package org.gradle.ide.visualstudio

import org.gradle.language.VariantContext
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppSourceElement

class VisualStudioCppApplicationProjectIntegrationTest extends AbstractVisualStudioProjectIntegrationTest {
    @Override
    void makeSingleProject() {
        buildFile << """
            apply plugin: "cpp-application"
        """
    }

    @Override
    String getComponentUnderTestDsl() {
        return "application"
    }

    @Override
    CppSourceElement getComponentUnderTest() {
        return new CppApp()
    }

    @Override
    String getBuildFile(VariantContext variantContext) {
        return executableName("build/install/main/${variantContext.asPath}lib/${rootProjectName}")
    }

    @Override
    String getIdeBuildTaskName(String variant) {
        return "install${variant.capitalize()}"
    }

    @Override
    List<String> getTasksToBuildFromIde(String variant) {
        return [":compile${variant.capitalize()}Cpp", ":link${variant.capitalize()}", ":install${variant.capitalize()}"]
    }

    @Override
    String getVisualStudioProjectName() {
        return rootProjectName
    }
}
