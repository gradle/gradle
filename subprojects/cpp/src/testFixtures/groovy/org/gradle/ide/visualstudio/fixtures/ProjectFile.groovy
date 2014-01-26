/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.fixtures
import org.gradle.test.fixtures.file.TestFile

class ProjectFile {
    String name
    TestFile projectFile
    Node projectXml

    ProjectFile(TestFile projectFile) {
        assert projectFile.exists()
        this.projectFile = projectFile
        this.name = projectFile.name.replace(".vcxproj", "")
        this.projectXml = new XmlParser().parse(projectFile)
    }

    public Map<String, Configuration> getProjectConfigurations() {
        def configs = itemGroup("ProjectConfigurations").collect {
            new Configuration(it.Configuration[0].text(), it.Platform[0].text())
        }
        return configs.collectEntries {
            [it.name, it]
        }
    }

    public String getProjectGuid() {
        return globals.ProjectGUID[0].text()
    }

    public Node getGlobals() {
        return projectXml.PropertyGroup.find({it.'@Label' == 'Globals'}) as Node
    }

    public List<String> getSourceFiles() {
        def sources = itemGroup('Sources').ClCompile
        return sources*.'@Include'
    }

    public List<String> getResourceFiles() {
        def sources = itemGroup('References').ResourceCompile
        return sources*.'@Include'
    }

    public List<String> getHeaderFiles() {
        def sources = itemGroup('Headers').ClInclude
        return sources*.'@Include'
    }

    private Node itemGroup(String label) {
        return projectXml.ItemGroup.find({it.'@Label' == label}) as Node
    }

    class Configuration {
        String name
        String platformName

        Configuration(String name, String platformName) {
            this.name = name
            this.platformName = platformName
        }

        String getMacros() {
            buildConfiguration.NMakePreprocessorDefinitions[0].text()
        }

        String getIncludePath() {
            buildConfiguration.NMakeIncludeSearchPath[0].text()
        }

        ProjectFile getProject() {
            return ProjectFile.this
        }

        String getBuildCommand() {
            buildConfiguration.NMakeBuildCommandLine[0].text()
        }

        String getOutputFile() {
            buildConfiguration.NMakeOutput[0].text()
        }

        private Node getBuildConfiguration() {
            projectXml.PropertyGroup.find({ it.'@Label' == 'NMakeConfiguration' && it.'@Condition' == condition}) as Node
        }

        private String getCondition() {
            "'\$(Configuration)|\$(Platform)'=='${name}|${platformName}'"
        }
    }
}
