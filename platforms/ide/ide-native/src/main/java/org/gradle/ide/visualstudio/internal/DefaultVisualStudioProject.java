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

package org.gradle.ide.visualstudio.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.ide.visualstudio.XmlConfigFile;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.plugins.ide.internal.IdeProjectMetadata;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * A VisualStudio project represents a set of binaries for a component that may vary in build type and target platform.
 */
public class DefaultVisualStudioProject implements VisualStudioProjectInternal {
    private final DefaultConfigFile projectFile;
    private final DefaultConfigFile filtersFile;
    private final String name;
    private final String componentName;
    private final Property<VersionNumber> visualStudioVersion;
    private final Property<VersionNumber> sdkVersion;
    private final List<File> additionalFiles = new ArrayList<>();
    private final Map<VisualStudioTargetBinary, VisualStudioProjectConfiguration> configurations = new LinkedHashMap<VisualStudioTargetBinary, VisualStudioProjectConfiguration>();
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final ConfigurableFileCollection sourceFiles;
    private final ConfigurableFileCollection headerFiles;

    public DefaultVisualStudioProject(String name, String componentName, PathToFileResolver fileResolver, ObjectFactory objectFactory, ProviderFactory providerFactory) {
        this.name = name;
        this.componentName = componentName;
        this.visualStudioVersion = objectFactory.property(VersionNumber.class).convention(AbstractCppBinaryVisualStudioTargetBinary.DEFAULT_VISUAL_STUDIO_VERSION);
        this.sdkVersion = objectFactory.property(VersionNumber.class).convention(AbstractCppBinaryVisualStudioTargetBinary.DEFAULT_SDK_VERSION);
        this.projectFile = objectFactory.newInstance(DefaultConfigFile.class, fileResolver, getName() + ".vcxproj");
        this.filtersFile = objectFactory.newInstance(DefaultConfigFile.class, fileResolver, getName() + ".vcxproj.filters");
        this.sourceFiles = objectFactory.fileCollection().from(providerFactory.provider(() -> {
            Set<File> allSourcesFromBinaries = new LinkedHashSet<>();
            for (VisualStudioTargetBinary binary : configurations.keySet()) {
                allSourcesFromBinaries.addAll(binary.getSourceFiles().getFiles());
            }
            return allSourcesFromBinaries;
        }), providerFactory.provider(() -> additionalFiles));
        this.headerFiles = objectFactory.fileCollection().from(providerFactory.provider(() -> {
            Set<File> allHeadersFromBinaries = new LinkedHashSet<File>();
            for (VisualStudioTargetBinary binary : configurations.keySet()) {
                allHeadersFromBinaries.addAll(binary.getHeaderFiles().getFiles());
            }
            return allHeadersFromBinaries;
        }));
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    public DefaultConfigFile getProjectFile() {
        return projectFile;
    }

    @Override
    public DefaultConfigFile getFiltersFile() {
        return filtersFile;
    }

    public void addSourceFile(File sourceFile) {
        additionalFiles.add(sourceFile);
    }

    public static String getUUID(File projectFile) {
        return "{" + UUID.nameUUIDFromBytes(projectFile.getAbsolutePath().getBytes()).toString().toUpperCase(Locale.ROOT) + "}";
    }

    @Internal
    public ConfigurableFileCollection getSourceFiles() {
        return sourceFiles;
    }

    @Input
    public Set<String> getSourceFilePaths() {
        return collect(getSourceFiles().getFiles(), File::getAbsolutePath);
    }

    @Internal
    public Set<File> getResourceFiles() {
        Set<File> allResources = new LinkedHashSet<File>();
        for (VisualStudioTargetBinary binary : configurations.keySet()) {
            allResources.addAll(binary.getResourceFiles().getFiles());
        }
        return allResources;
    }

    @Input
    public Set<String> getResourceFilePaths() {
        return collect(getResourceFiles(), File::getAbsolutePath);
    }

    @Internal
    public ConfigurableFileCollection getHeaderFiles() {
        return headerFiles;
    }

    @Input
    public Set<String> getHeaderFilePaths() {
        return collect(getHeaderFiles().getFiles(), File::getAbsolutePath);
    }

    @Nested
    public List<VisualStudioProjectConfiguration> getConfigurations() {
        if (configurations.isEmpty()) {
            return ImmutableList.of(new VisualStudioProjectConfiguration(this, "unbuildable", null));
        }
        return CollectionUtils.toList(configurations.values());
    }

    public void addConfiguration(VisualStudioTargetBinary nativeBinary, VisualStudioProjectConfiguration configuration) {
        configurations.put(nativeBinary, configuration);
        builtBy(nativeBinary.getSourceFiles());
        builtBy(nativeBinary.getResourceFiles());
        builtBy(nativeBinary.getHeaderFiles());
    }

    public VisualStudioProjectConfiguration getConfiguration(VisualStudioTargetBinary nativeBinary) {
        return configurations.get(nativeBinary);
    }

    @Override
    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public String getName() {
        return name;
    }

    @Internal
    public Property<VersionNumber> getVisualStudioVersion() {
        return visualStudioVersion;
    }

    @Internal
    public Property<VersionNumber> getSdkVersion() {
        return sdkVersion;
    }

    @Override
    public IdeProjectMetadata getPublishArtifact() {
        return new VisualStudioProjectMetadata(this);
    }

    @Nested
    public List<Action<? super XmlProvider>> getProjectFileActions() {
        return projectFile.getXmlActions();
    }

    @Nested
    public List<Action<? super XmlProvider>> getFiltersFileActions() {
        return filtersFile.getXmlActions();
    }

    public static class DefaultConfigFile implements XmlConfigFile {
        private final List<Action<? super XmlProvider>> actions = new ArrayList<Action<? super XmlProvider>>();
        private final PathToFileResolver fileResolver;
        private Object location;

        @Inject
        public DefaultConfigFile(PathToFileResolver fileResolver, String defaultLocation) {
            this.fileResolver = fileResolver;
            this.location = defaultLocation;
        }

        @Override
        public File getLocation() {
            return fileResolver.resolve(location);
        }

        @Override
        public void setLocation(Object location) {
            this.location = location;
        }

        @Override
        public void withXml(Action<? super XmlProvider> action) {
            actions.add(action);
        }

        @Nested
        public List<Action<? super XmlProvider>> getXmlActions() {
            return actions;
        }
    }
}
