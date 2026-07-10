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

import groovy.xml.XmlParser
import org.gradle.integtests.fixtures.SourceFile
import org.gradle.nativeplatform.fixtures.app.CppSourceElement
import org.gradle.nativeplatform.fixtures.app.TestNativeComponent
import org.gradle.plugins.ide.fixtures.IdeProjectFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

import javax.annotation.Nullable

class ProjectFile extends IdeProjectFixture {
    String name
    TestFile projectFile
    Node projectXml

    ProjectFile(TestFile projectFile) {
        projectFile.assertIsFile()
        this.projectFile = projectFile
        this.name = projectFile.name.replace(".vcxproj", "")
        this.projectXml = new XmlParser().parse(projectFile)
    }

    Map<String, Configuration> getProjectConfigurations() {
        def configs = itemGroup("ProjectConfigurations").collect {
            new Configuration(it.Configuration[0].text(), it.Platform[0].text())
        }
        return configs.collectEntries {
            [it.name, it]
        }
    }

    String getProjectGuid() {
        return globals.ProjectGUID[0].text()
    }

    Node getGlobals() {
        return projectXml.PropertyGroup.find({it.'@Label' == 'Globals'}) as Node
    }

    String getToolsVersion() {
        return projectXml.@ToolsVersion
    }

    String getWindowsTargetPlatformVersion() {
        return globals.WindowsTargetPlatformVersion[0].text()
    }

    List<String> getSourceFiles() {
        def sources = itemGroup('Sources').ClCompile
        return normalise(sources*.'@Include')
    }

    List<String> getResourceFiles() {
        def sources = itemGroup('References').ResourceCompile
        return normalise(sources*.'@Include')
    }

    List<String> getHeaderFiles() {
        def sources = itemGroup('Headers').ClInclude
        return normalise(sources*.'@Include')
    }

    private static List<String> normalise(List<String> files) {
        return files.collect({ TextUtil.normaliseFileSeparators(it)}).sort()
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

        ProjectFile getProject() {
            return ProjectFile.this
        }

        String getOutputDir() {
            return name.replaceFirst("(\\p{Upper})", "/\$1").toLowerCase()
        }

        String getMacros() {
            buildConfiguration.NMakePreprocessorDefinitions[0].text()
        }

        String getIncludePath() {
            TextUtil.normaliseFileSeparators(buildConfiguration.NMakeIncludeSearchPath[0].text())
        }

        String getBuildCommand() {
            TextUtil.normaliseFileSeparators(buildConfiguration.NMakeBuildCommandLine[0].text())
        }

        String getOutputFile() {
            TextUtil.normaliseFileSeparators(buildConfiguration.NMakeOutput[0].text())
        }

        @Nullable
        String getLanguageStandard() {
            def itemDefinitionGroupNode = projectXml.ItemDefinitionGroup.find({ it.'@Condition' == condition }) as Node
            if (itemDefinitionGroupNode == null) {
                return null
            }
            return itemDefinitionGroupNode.ClCompile[0].LanguageStandard[0].text()
        }

        String getPlatformToolset() {
            def nodes = configuration.PlatformToolset
            return nodes.size() == 0 ? null : nodes[0].text()
        }

        private Node getBuildConfiguration() {
            projectXml.PropertyGroup.find({ it.'@Label' == 'NMakeConfiguration' && it.'@Condition' == condition}) as Node
        }

        private Node getConfiguration() {
            projectXml.PropertyGroup.find({ it.'@Label' == 'Configuration' && it.'@Condition' == condition}) as Node
        }

        private String getCondition() {
            "'\$(Configuration)|\$(Platform)'=='${name}|${platformName}'"
        }
    }

    void assertHasComponentSources(TestNativeComponent component, String basePath) {
        assert sourceFiles == ['build.gradle'] + sourceFiles(component.sourceFiles, basePath)
        assert headerFiles == sourceFiles(component.headerFiles, basePath)
    }

    void assertHasComponentSources(CppSourceElement component, String basePath) {
        assert sourceFiles == ['build.gradle'] + sourceFiles(component.sources.files, basePath)
        assert headerFiles == sourceFiles(component.headers.files, basePath)
    }

    void assertHasComponentSources(TestNativeComponent component, String basePath, TestNativeComponent component2, String basePath2) {
        assert sourceFiles == ['build.gradle'] + sourceFiles(component.sourceFiles, basePath) + sourceFiles(component2.sourceFiles, basePath2)
        assert headerFiles == sourceFiles(component.headerFiles, basePath) + sourceFiles(component2.headerFiles, basePath2)
    }

    private static List<String> sourceFiles(List<SourceFile> files, String path) {
        return files*.withPath(path).sort()
    }

}
