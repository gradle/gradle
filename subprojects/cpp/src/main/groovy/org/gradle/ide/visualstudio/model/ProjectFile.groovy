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


package org.gradle.ide.visualstudio.model
import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

class ProjectFile extends XmlPersistableConfigurationObject {
    ProjectFile() {
        super(new XmlTransformer())
    }

    protected String getDefaultResourceName() {
        'default.vcxproj'
    }

    def setProjectUuid(String uuid) {
        Node globals = xml.PropertyGroup.find({it.'@Label' == 'Globals'}) as Node
        globals.appendNode("ProjectGUID", uuid)
    }

    def addSourceFile(String it) {
        def sources = xml.ItemGroup.find({ it.'@Label' == 'Sources' }) as Node
        sources.appendNode("ClCompile", [Include: it])
    }

    def addHeaderFile(String it) {
        def headers = xml.ItemGroup.find({ it.'@Label' == 'Headers' }) as Node
        headers.appendNode("ClInclude", [Include: it])
    }

    def addConfiguration(VisualStudioProjectConfiguration configuration) {
        def configNode = configurations.appendNode("ProjectConfiguration", [Include: configuration.name])
        configNode.appendNode("Configuration", configuration.configurationName)
        configNode.appendNode("Platform", configuration.platformName)

        Node defaultProps = xml.Import.find({ it.'@Project' == '$(VCTargetsPath)\\Microsoft.Cpp.Default.props'}) as Node
        defaultProps + {
            PropertyGroup(Label: "Configuration", Condition: "'\$(Configuration)|\$(Platform)'=='${configuration.name}'") {
                ConfigurationType(configuration.type)
                UseDebugLibraries(configuration.debug)
            }
        }

        final configCondition = "'\$(Configuration)|\$(Platform)'=='${configuration.name}'"
        Node userMacros = xml.PropertyGroup.find({ it.'@Label' == 'UserMacros'}) as Node
        userMacros + {
            PropertyGroup(Condition: configCondition) {
                NMakeBuildCommandLine("gradlew.bat ${configuration.buildTask}")
                NMakeCleanCommandLine("gradlew.bat ${configuration.cleanTask}")
                NMakeReBuildCommandLine("gradlew.bat ${configuration.cleanTask} ${configuration.buildTask}")
                NMakePreprocessorDefinitions(configuration.defines.join(";"))
                NMakeIncludeSearchPath(configuration.includePaths.join(";"))
                NMakeOutput(configuration.outputFile)
            }
            ItemDefinitionGroup(Condition: configCondition) {
                ClCompile {
                    AdditionalIncludeDirectories(configuration.includePaths.join(";"))
                    PreprocessorDefinitions(configuration.defines.join(";"))
                }
            }
        }
    }

    def addProjectReference(VisualStudioLibraryProject visualStudioLibraryProject) {
        Node references = xml.ItemGroup.find({ it.'@Label' == 'References' }) as Node
        references.appendNode("ProjectReference", [Include: visualStudioLibraryProject.projectFile])
                  .appendNode("Project", visualStudioLibraryProject.uuid)
    }

    private Node getConfigurations() {
        return xml.ItemGroup.find({ it.'@Label' == 'ProjectConfigurations' }) as Node
    }
}