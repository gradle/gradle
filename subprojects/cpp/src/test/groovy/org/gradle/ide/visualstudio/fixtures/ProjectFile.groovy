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
    Node projectXml

    ProjectFile(TestFile projectFile) {
        assert projectFile.exists()
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

    private Node getGlobals() {
        return projectXml.PropertyGroup.find({it.'@Label' == 'Globals'}) as Node
    }

    public List<String> getSourceFiles() {
        def sources = itemGroup('Sources').ClCompile
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
        String configName
        String platformName

        Configuration(String configName, String platformName) {
            this.configName = configName
            this.platformName = platformName
        }

        String getName() {
            "${configName}|${platformName}"
        }

        String getMacros() {
            buildConfiguration.ClCompile[0].PreprocessorDefinitions[0].text()
        }

        String getIncludePath() {
            buildConfiguration.ClCompile[0].AdditionalIncludeDirectories[0].text()
        }

        private Node getBuildConfiguration() {
            projectXml.ItemDefinitionGroup.find({ it.'@Label' == 'VSBuildConfiguration' && it.'@Condition' == condition}) as Node
        }

        private String getCondition() {
            "'\$(Configuration)|\$(Platform)'=='${configName}|${platformName}'"
        }
    }
}
