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
import org.gradle.api.Transformer
import org.gradle.internal.xml.XmlTransformer
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfiguration
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

class VisualStudioProjectFile extends XmlPersistableConfigurationObject {
    private final Transformer<String, File> fileLocationResolver
    String gradleCommand = 'gradle'

    VisualStudioProjectFile(XmlTransformer xmlTransformer, Transformer<String, File> fileLocationResolver) {
        super(xmlTransformer)
        this.fileLocationResolver = fileLocationResolver
    }

    protected String getDefaultResourceName() {
        'default.vcxproj'
    }

    def setProjectUuid(String uuid) {
        Node globals = xml.PropertyGroup.find({it.'@Label' == 'Globals'}) as Node
        globals.appendNode("ProjectGUID", uuid)
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

    def addConfiguration(VisualStudioProjectConfiguration configuration) {
        def configNode = configurations.appendNode("ProjectConfiguration", [Include: configuration.name])
        configNode.appendNode("Configuration", configuration.configurationName)
        configNode.appendNode("Platform", configuration.platformName)
        final configCondition = "'\$(Configuration)|\$(Platform)'=='${configuration.name}'"

        def vsOutputDir = ".vs\\${configuration.project.name}\\\$(Configuration)"
        Node defaultProps = xml.Import.find({ it.'@Project' == '$(VCTargetsPath)\\Microsoft.Cpp.Default.props'}) as Node
        defaultProps + {
            PropertyGroup(Label: "Configuration", Condition: configCondition) {
                ConfigurationType(configuration.type)
                UseDebugLibraries(configuration.debug)
                OutDir(vsOutputDir)
                IntDir(vsOutputDir)
            }
        }

        final includePath = toPath(configuration.includePaths).join(";")
        Node userMacros = xml.PropertyGroup.find({ it.'@Label' == 'UserMacros'}) as Node
        userMacros + {
            PropertyGroup(Label: "NMakeConfiguration", Condition: configCondition) {
                NMakeBuildCommandLine("${gradleCommand} ${configuration.buildTask}")
                NMakeCleanCommandLine("${gradleCommand} ${configuration.cleanTask}")
                NMakeReBuildCommandLine("${gradleCommand} ${configuration.cleanTask} ${configuration.buildTask}")
                NMakePreprocessorDefinitions(configuration.compilerDefines.join(";"))
                NMakeIncludeSearchPath(includePath)
                NMakeOutput(toPath(configuration.outputFile))
            }
        }
    }

    private Node getConfigurations() {
        return xml.ItemGroup.find({ it.'@Label' == 'ProjectConfigurations' }) as Node
    }

    private List<String> toPath(List<File> files) {
        return files.collect({toPath(it)})
    }

    private String toPath(File it) {
        fileLocationResolver.transform(it)
    }
}