/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.api.Transformer;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;
import org.gradle.util.internal.VersionNumber;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

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
        getItemGroupForLabel("Sources").appendNode(
            "ClCompile",
            singletonMap("Include", toPath(file))
        );
    }

    public void addResource(File file) {
        getItemGroupForLabel("References").appendNode(
            "ResourceCompile",
            singletonMap("Include", toPath(file))
        );
    }

    public void addHeaderFile(File file) {
        getItemGroupForLabel("Headers").appendNode(
            "ClInclude",
            singletonMap("Include", toPath(file))
        );
    }

    public void addConfiguration(ConfigurationSpec configuration) {
        Node configNode = getItemGroupForLabel("ProjectConfigurations").appendNode(
            "ProjectConfiguration",
            singletonMap("Include", configuration.getName())
        );
        configNode.appendNode("Configuration", configuration.configurationName);
        configNode.appendNode("Platform", configuration.platformName);
        String configCondition = "'$(Configuration)|$(Platform)'=='" + configuration.getName() + "'";

        String vsOutputDir = ".vs\\" + configuration.projectName + "\\$(Configuration)\\";
        Node configGroup = appendDirectSibling(
            getImportsForProject("$(VCTargetsPath)\\Microsoft.Cpp.Default.props"),
            "PropertyGroup",
            new LinkedHashMap<String, String>() {{
                put("Label", "Configuration");
                put("Condition", configCondition);
            }}
        );
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
        Node nMakeGroup = appendDirectSibling(
            getPropertyGroupForLabel("UserMacros"),
            "PropertyGroup",
            new LinkedHashMap<String, String>() {{
                put("Label", "NMakeConfiguration");
                put("Condition", configCondition);
            }}
        );
        if (configuration.buildable) {
            nMakeGroup.appendNode("NMakeBuildCommandLine", gradleCommand + " " + configuration.buildTaskPath);
            nMakeGroup.appendNode("NMakeCleanCommandLine", gradleCommand + " " + configuration.cleanTaskPath);
            nMakeGroup.appendNode("NMakeReBuildCommandLine", gradleCommand + " " + configuration.cleanTaskPath + " " + configuration.buildTaskPath);
            nMakeGroup.appendNode("NMakePreprocessorDefinitions", String.join(";", configuration.compilerDefines));
            nMakeGroup.appendNode("NMakeIncludeSearchPath", includePath);
            nMakeGroup.appendNode("NMakeOutput", toPath(configuration.outputFile));
        } else {
            String errorCommand = "echo '" + configuration.projectName + "' project is not buildable. && exit /b -42";
            nMakeGroup.appendNode("NMakeBuildCommandLine", errorCommand);
            nMakeGroup.appendNode("NMakeCleanCommandLine", errorCommand);
            nMakeGroup.appendNode("NMakeReBuildCommandLine", errorCommand);
        }

        if (configuration.languageStandard != null && configuration.languageStandard != VisualStudioTargetBinary.LanguageStandard.NONE) {
            getXml().appendNode("ItemDefinitionGroup", singletonMap("Condition", configCondition))
                .appendNode("ClCompile")
                .appendNode("LanguageStandard", configuration.languageStandard.getValue());
        }
    }

    private Node getItemGroupForLabel(String label) {
        return getSingleNodeWithAttribute("ItemGroup", "Label", label);
    }

    private Node getPropertyGroupForLabel(String label) {
        return getSingleNodeWithAttribute("PropertyGroup", "Label", label);
    }

    private Node getImportsForProject(String project) {
        return getSingleNodeWithAttribute("Import", "Project", project);
    }

    private Node getSingleNodeWithAttribute(String nodeName, String attributeName, String attributeValue) {
        return Objects.requireNonNull(
            findFirstChildWithAttributeValue(getXml(), nodeName, attributeName, attributeValue),
            "No '" + nodeName + "' with attribute '" + attributeName + " = " + attributeValue + "' found"
        );
    }

    private List<String> toPath(Set<File> files) {
        return files.stream().map(this::toPath).collect(toList());
    }

    private String toPath(File file) {
        return fileLocationResolver.transform(file);
    }

    /**
     * Replicates the behaviour of {@link Node#plus(groovy.lang.Closure)}.
     */
    @SuppressWarnings("unchecked")
    private static Node appendDirectSibling(Node node, String siblingName, LinkedHashMap<String, String> siblingAttributes) {
        if (node.parent() == null) {
            throw new UnsupportedOperationException("Adding sibling nodes to the root node is not supported");
        }
        // Grab tail
        List<?> parentChildren = node.parent().children();
        int afterIndex = parentChildren.indexOf(node);
        List<?> tail = new ArrayList<>(parentChildren.subList(afterIndex + 1, parentChildren.size()));
        parentChildren.subList(afterIndex + 1, parentChildren.size()).clear();
        // Add sibling
        Node sibling = node.parent().appendNode(siblingName, siblingAttributes);
        // Restore tail
        node.parent().children().addAll(tail);
        return sibling;
    }

    public static class ConfigurationSpec {
        private final String name;
        private final String configurationName;
        private final String projectName;
        private final String platformName;
        private final String type;
        private final boolean buildable;
        private final boolean debuggable;
        private final Set<File> includeDirs;
        private final String buildTaskPath;
        private final String cleanTaskPath;
        private final List<String> compilerDefines;
        private final File outputFile;
        private final VisualStudioTargetBinary.LanguageStandard languageStandard;

        public ConfigurationSpec(String name, String configurationName, String projectName, String platformName, String type, boolean buildable, boolean debuggable, Set<File> includeDirs, @Nullable String buildTaskPath, @Nullable String cleanTaskPath, List<String> compilerDefines, @Nullable File outputFile, VisualStudioTargetBinary.@Nullable LanguageStandard languageStandard) {
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
        public String getName() {
            return name;
        }

        @Input
        public String getConfigurationName() {
            return configurationName;
        }

        @Input
        public String getProjectName() {
            return projectName;
        }

        @Input
        public String getPlatformName() {
            return platformName;
        }

        @Input
        public String getType() {
            return type;
        }

        @Input
        public boolean isBuildable() {
            return buildable;
        }

        @Input
        public boolean isDebuggable() {
            return debuggable;
        }

        @Input
        @Optional
        @Nullable
        public String getBuildTaskPath() {
            return buildTaskPath;
        }

        @Input
        @Optional
        @Nullable
        public String getCleanTaskPath() {
            return cleanTaskPath;
        }

        @Input
        public List<String> getCompilerDefines() {
            return compilerDefines;
        }

        @Input
        @Optional
        public VisualStudioTargetBinary.@Nullable LanguageStandard getLanguageStandard() {
            return languageStandard;
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
