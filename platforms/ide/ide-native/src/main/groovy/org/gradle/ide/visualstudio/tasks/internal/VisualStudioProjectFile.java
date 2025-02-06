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

package org.gradle.ide.visualstudio.tasks.internal

import org.gradle.api.NonNullApi
import org.gradle.api.Transformer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject
import org.gradle.util.internal.VersionNumber

import javax.annotation.Nullable

@NonNullApi
class VisualStudioProjectFile extends XmlPersistableConfigurationObject {
    private final Transformer<String, File> fileLocationResolver
    String gradleCommand = 'gradle'
    VersionNumber visualStudioVersion

    VisualStudioProjectFile(XmlTransformer xmlTransformer, Transformer<String, File> fileLocationResolver) {
        super(xmlTransformer)
        this.fileLocationResolver = fileLocationResolver
    }

    protected String getDefaultResourceName() {
        'default.vcxproj'
    }

    def setProjectUuid(String uuid) {
        Node globals = xml.PropertyGroup.find({ it.'@Label' == 'Globals' }) as Node
        globals.appendNode("ProjectGUID", uuid)
    }

    def setVisualStudioVersion(VersionNumber version) {
        visualStudioVersion = version
        xml.attributes().ToolsVersion = version.major >= 12 ? "${version.major}.0" : "4.0"
    }

    def setSdkVersion(VersionNumber version) {
        Node globals = xml.PropertyGroup.find({ it.'@Label' == 'Globals' }) as Node
        globals.appendNode("WindowsTargetPlatformVersion", version.micro != 0 ? version : "${version.major}.${version.minor}")
    }

    def addSourceFile(File it) {
        def sources = xml.ItemGroup.find({ it.'@Label' == 'Sources' }) as Node
        sources.appendNode("ClCompile", [Include: toPath(it)])
    }

    def addResource(File it) {
        def resources = xml.ItemGroup.find({ it.'@Label' == 'References' }) as Node
        resources.appendNode("ResourceCompile", [Include: toPath(it)])
    }

    def addHeaderFile(File it) {
        def headers = xml.ItemGroup.find({ it.'@Label' == 'Headers' }) as Node
        headers.appendNode("ClInclude", [Include: toPath(it)])
    }

    def addConfiguration(ConfigurationSpec configuration) {
        def configNode = configurations.appendNode("ProjectConfiguration", [Include: configuration.name])
        configNode.appendNode("Configuration", configuration.configurationName)
        configNode.appendNode("Platform", configuration.platformName)
        final configCondition = "'\$(Configuration)|\$(Platform)'=='${configuration.name}'"

        def vsOutputDir = ".vs\\${configuration.projectName}\\\$(Configuration)"
        Node defaultProps = xml.Import.find({ it.'@Project' == '$(VCTargetsPath)\\Microsoft.Cpp.Default.props' }) as Node
        defaultProps + {
            PropertyGroup(Label: "Configuration", Condition: configCondition) {
                ConfigurationType(configuration.type)
                if (configuration.buildable) {
                    UseDebugLibraries(configuration.debuggable)
                    OutDir(vsOutputDir)
                    IntDir(vsOutputDir)
                }
                if (visualStudioVersion.major > 14) {
                    PlatformToolset("v141")
                } else if (visualStudioVersion.major >= 11) {
                    PlatformToolset("v${visualStudioVersion.major}0")
                }
            }
        }

        final includePath = toPath(configuration.buildable ? configuration.includeDirs : [] as Set).join(";")
        Node userMacros = xml.PropertyGroup.find({ it.'@Label' == 'UserMacros' }) as Node
        userMacros + {
            PropertyGroup(Label: "NMakeConfiguration", Condition: configCondition) {
                if (configuration.buildable) {
                    NMakeBuildCommandLine("${gradleCommand} ${configuration.buildTaskPath}")
                    NMakeCleanCommandLine("${gradleCommand} ${configuration.cleanTaskPath}")
                    NMakeReBuildCommandLine("${gradleCommand} ${configuration.cleanTaskPath} ${configuration.buildTaskPath}")
                    NMakePreprocessorDefinitions(configuration.compilerDefines.join(";"))
                    NMakeIncludeSearchPath(includePath)
                    NMakeOutput(toPath(configuration.outputFile))
                } else {
                    NMakeBuildCommandLine("echo '${configuration.projectName}' project is not buildable. && exit /b -42")
                    NMakeCleanCommandLine("echo '${configuration.projectName}' project is not buildable. && exit /b -42")
                    NMakeReBuildCommandLine("echo '${configuration.projectName}' project is not buildable. && exit /b -42")
                }
            }
        }

        if (configuration.languageStandard != null && configuration.languageStandard != VisualStudioTargetBinary.LanguageStandard.NONE) {
            xml.appendNode("ItemDefinitionGroup", [Condition: configCondition]).appendNode("ClCompile").appendNode("LanguageStandard", configuration.languageStandard.value)
        }
    }

    private Node getConfigurations() {
        return xml.ItemGroup.find({ it.'@Label' == 'ProjectConfigurations' }) as Node
    }

    private List<String> toPath(Set<File> files) {
        return files.collect({ toPath(it) })
    }

    private String toPath(File it) {
        fileLocationResolver.transform(it)
    }

    @NonNullApi
    static class ConfigurationSpec {
        @Input
        final String name
        @Input
        final String configurationName
        @Input
        final String projectName
        @Input
        final String platformName
        @Input
        final String type
        @Input
        final boolean buildable
        @Input
        final boolean debuggable
        @Internal
        final Set<File> includeDirs
        @Input
        @Optional
        final String buildTaskPath
        @Input
        @Optional
        final String cleanTaskPath
        @Input
        final List<String> compilerDefines
        @Internal
        @Nullable
        final File outputFile
        @Input
        @Optional
        final VisualStudioTargetBinary.LanguageStandard languageStandard

        ConfigurationSpec(String name, String configurationName, String projectName, String platformName, String type, boolean buildable, boolean debuggable, Set<File> includeDirs, @Nullable String buildTaskPath, @Nullable String cleanTaskPath, List<String> compilerDefines, @Nullable File outputFile, @Nullable VisualStudioTargetBinary.LanguageStandard languageStandard) {
            this.name = name
            this.configurationName = configurationName
            this.projectName = projectName
            this.platformName = platformName
            this.type = type
            this.buildable = buildable
            this.debuggable = debuggable
            this.includeDirs = includeDirs
            this.buildTaskPath = buildTaskPath
            this.cleanTaskPath = cleanTaskPath
            this.compilerDefines = compilerDefines
            this.outputFile = outputFile
            this.languageStandard = languageStandard
        }

        @Input
        Collection<String> getIncludeDirPaths() {
            return includeDirs.collect { it.absolutePath }
        }

        @Input
        @Optional
        String getOutputFilePath() {
            return outputFile?.absolutePath
        }
    }
}
