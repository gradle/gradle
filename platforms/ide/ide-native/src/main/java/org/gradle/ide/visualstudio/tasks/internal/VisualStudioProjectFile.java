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

package org.gradle.ide.visualstudio.tasks.internal;

import groovy.util.Node;
import org.gradle.api.NonNullApi;
import org.gradle.api.Transformer;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;
import org.gradle.util.internal.VersionNumber;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

@NonNullApi
public class VisualStudioProjectFile extends XmlPersistableConfigurationObject {
    private final Transformer<String, File> fileLocationResolver;
    private String gradleCommand = "gradle";
    private VersionNumber visualStudioVersion;

    public VisualStudioProjectFile(XmlTransformer xmlTransformer, Transformer<String, File> fileLocationResolver) {
        super(xmlTransformer);
        this.fileLocationResolver = fileLocationResolver;
    }

    @Override
    protected String getDefaultResourceName() {
        return "default.vcxproj";
    }

    public void setGradleCommand(String gradleCommand) {
        this.gradleCommand = gradleCommand;
    }

    public void setProjectUuid(String uuid) {
        getPropertyGroupForLabel("Globals").appendNode("ProjectGUID", uuid);
    }

    @SuppressWarnings("unchecked")
    public void setVisualStudioVersion(VersionNumber version) {
        visualStudioVersion = version;
        getXml().attributes().put("ToolsVersion", version.getMajor() >= 12 ? version.getMajor() + ".0" : "4.0");
    }

    public void setSdkVersion(VersionNumber version) {
        getPropertyGroupForLabel("Globals").appendNode(
            "WindowsTargetPlatformVersion",
            version.getMicro() != 0 ? version : version.getMajor() + "." + version.getMinor()
        );
    }

    public void addSourceFile(File file) {
        getItemGroupForLabel("Sources").appendNode("ClCompile", singletonMap("Include", toPath(file)));
    }

    public void addResource(File file) {
        getItemGroupForLabel("References").appendNode("ResourceCompile", singletonMap("Include", toPath(file)));
    }

    public void addHeaderFile(File file) {
        getItemGroupForLabel("Headers").appendNode("ClInclude", singletonMap("Include", toPath(file)));
    }

    public void addConfiguration(ConfigurationSpec configuration) {
        Node configNode = getItemGroupForLabel("ProjectConfigurations")
            .appendNode("ProjectConfiguration", singletonMap("Include", configuration.name));
        configNode.appendNode("Configuration", configuration.configurationName);
        configNode.appendNode("Platform", configuration.platformName);
        String configCondition = "'$(Configuration)|$(Platform)'=='" + configuration.name + "'";

        String vsOutputDir = ".vs\\" + configuration.projectName + "\\$(Configuration)";
        Node configGroup = getImportsForProject("$(VCTargetsPath)\\Microsoft.Cpp.Default.props").parent()
            .appendNode("PropertyGroup", new HashMap<String, String>() {
                {
                    put("Label", "Configuration");
                    put("Condition", configCondition);
                }
            });
        configGroup.appendNode("ConfigurationType", configuration.type);
        if (configuration.buildable) {
            configGroup.appendNode("UseDebugLibraries", configuration.debuggable);
            configGroup.appendNode("OutDir", vsOutputDir);
            configGroup.appendNode("IntDir", vsOutputDir);
        }
        if (visualStudioVersion.getMajor() > 14) {
            configGroup.appendNode("PlatformToolset", "v141");
        } else if (visualStudioVersion.getMajor() >= 11) {
            configGroup.appendNode("PlatformToolset", "v" + visualStudioVersion.getMajor() + "0");
        }

        String includePath = String.join(";", toPath(configuration.buildable ? configuration.includeDirs : emptySet()));
        Node nMakeGroup = getPropertyGroupForLabel("UserMacros").parent()
            .appendNode("PropertyGroup", new HashMap<String, String>() {
                {
                    put("Label", "NMakeConfiguration");
                    put("Condition", configCondition);
                }
            });
        if (configuration.buildable) {
            nMakeGroup.appendNode("NMakeBuildCommandLine", gradleCommand + " " + configuration.buildTaskPath);
            nMakeGroup.appendNode("NMakeCleanCommandLine", gradleCommand + " " + configuration.cleanTaskPath);
            nMakeGroup.appendNode("NMakeReBuildCommandLine", gradleCommand + " " + configuration.cleanTaskPath + " " + configuration.buildTaskPath);
            nMakeGroup.appendNode("NMakePreprocessorDefinitions", String.join(";", configuration.compilerDefines));
            nMakeGroup.appendNode("NMakeIncludeSearchPath", includePath);
            nMakeGroup.appendNode("NMakeOutput", toPath(configuration.outputFile));
        } else {
            nMakeGroup.appendNode("NMakeBuildCommandLine", "echo '" + configuration.projectName + "' project is not buildable. && exit /b -42");
            nMakeGroup.appendNode("NMakeCleanCommandLine", "echo '" + configuration.projectName + "' project is not buildable. && exit /b -42");
            nMakeGroup.appendNode("NMakeReBuildCommandLine", "echo '" + configuration.projectName + "' project is not buildable. && exit /b -42");
        }

        if (configuration.languageStandard != null && configuration.languageStandard != VisualStudioTargetBinary.LanguageStandard.NONE) {
            getXml().appendNode("ItemDefinitionGroup", singletonMap("Condition", configCondition))
                .appendNode("ClCompile")
                .appendNode("LanguageStandard", configuration.languageStandard.getValue());
        }
    }

    private Node getItemGroupForLabel(String label) {
        return getChildren(getXml(), "ItemGroup").stream()
            .filter(node -> node.attribute("Label").equals(label))
            .findFirst().get();
    }

    private Node getPropertyGroupForLabel(String label) {
        return getChildren(getXml(), "PropertyGroup").stream()
            .filter(node -> node.attribute("Label").equals(label))
            .findFirst().get();
    }

    private Node getImportsForProject(String project) {
        return getChildren(getXml(), "Import").stream()
            .filter(node -> node.attribute("Project").equals(project))
            .findFirst().get();
    }

    private List<String> toPath(Set<File> files) {
        return files.stream().map(this::toPath).collect(toList());
    }

    private String toPath(File file) {
        return fileLocationResolver.transform(file);
    }

    @NonNullApi
    public static class ConfigurationSpec {
        @Input
        public final String name;
        @Input
        public final String configurationName;
        @Input
        public final String projectName;
        @Input
        public final String platformName;
        @Input
        public final String type;
        @Input
        public final boolean buildable;
        @Input
        public final boolean debuggable;
        @Internal
        public final Set<File> includeDirs;
        @Input
        @Optional
        public final String buildTaskPath;
        @Input
        @Optional
        public final String cleanTaskPath;
        @Input
        public final List<String> compilerDefines;
        @Internal
        @Nullable
        public final File outputFile;
        @Input
        @Optional
        public final VisualStudioTargetBinary.LanguageStandard languageStandard;

        public ConfigurationSpec(String name, String configurationName, String projectName, String platformName, String type, boolean buildable, boolean debuggable, Set<File> includeDirs, @Nullable String buildTaskPath, @Nullable String cleanTaskPath, List<String> compilerDefines, @Nullable File outputFile, @Nullable VisualStudioTargetBinary.LanguageStandard languageStandard) {
            this.name = name;
            this.configurationName = configurationName;
            this.projectName = projectName;
            this.platformName = platformName;
            this.type = type;
            this.buildable = buildable;
            this.debuggable = debuggable;
            this.includeDirs = includeDirs;
            this.buildTaskPath = buildTaskPath;
            this.cleanTaskPath = cleanTaskPath;
            this.compilerDefines = compilerDefines;
            this.outputFile = outputFile;
            this.languageStandard = languageStandard;
        }

        @Input
        public Collection<String> getIncludeDirPaths() {
            return includeDirs.stream().map(File::getAbsolutePath).collect(toList());
        }

        @Input
        @Optional
        @Nullable
        public String getOutputFilePath() {
            if (outputFile != null) {
                return outputFile.getAbsolutePath();
            }
            return null;
        }
    }
}
