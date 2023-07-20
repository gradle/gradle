/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.fixtures

import groovy.xml.slurpersupport.GPathResult
import org.gradle.test.fixtures.file.TestFile

class IdeaProjectFixture extends IdeWorkspaceFixture {
    private final GPathResult ipr
    private final TestFile file

    IdeaProjectFixture(TestFile file, GPathResult ipr) {
        this.file = file
        this.ipr = ipr
    }

    String getLanguageLevel() {
        if (ipr.component.@languageLevel.size() != 0) {
            return ipr.component.@languageLevel
        }
        return null
    }

    String getJdkName() {
        if (ipr.component."@project-jdk-name".size() != 0) {
            return ipr.component."@project-jdk-name"
        }
        return null
    }

    def getBytecodeTargetLevel() {
        def compilerConfig = ipr.component.find { it.@name == "CompilerConfiguration" }
        return compilerConfig.bytecodeTargetLevel
    }

    def getLibraryTable() {
        return ipr.component.find {
            it.@name == "libraryTable"
        }
    }

    ProjectModules getModules() {
        def projectModuleManager = ipr.component.find { it.@name == "ProjectModuleManager" }
        def moduleNames = projectModuleManager.modules.module.@filepath.collect {it.text()}
        return new ProjectModules(moduleNames)
    }

    @Override
    void assertContains(IdeProjectFixture project) {
        assert project instanceof IdeaModuleFixture
        def path = project.file.relativizeFrom(file.parentFile).path
        modules.modules.contains("\$PROJECT_DIR/$path")
    }

    static class ProjectModules {
        List<String> modules = []

        private ProjectModules(List<String> modules) {
            this.modules = modules
        }

        int size() {
            return modules.size()
        }

        void assertHasModule(String name) {
            assert modules.any { it.endsWith(name)} : "No module with $name found in ${modules}"
        }

        void assertHasModules(String... name) {
            List<String> modules = name.toList()
            assert this.modules.every { modules.contains(it) }
        }
    }

}
