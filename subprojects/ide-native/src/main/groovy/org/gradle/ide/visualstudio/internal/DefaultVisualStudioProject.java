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

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.AbstractBuildableComponentSpec;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.XmlConfigFile;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.language.rc.WindowsResourceSet;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A VisualStudio project represents a set of binaries for a component that may vary in build type and target platform.
 */
public class DefaultVisualStudioProject extends AbstractBuildableComponentSpec implements VisualStudioProject {
    private final DefaultConfigFile projectFile;
    private final DefaultConfigFile filtersFile;
    private final NativeComponentSpec component;
    private final List<File> additionalFiles = new ArrayList<File>();
    private final Set<LanguageSourceSet> sources = new LinkedHashSet<LanguageSourceSet>();
    private final Map<NativeBinarySpec, VisualStudioProjectConfiguration> configurations = new LinkedHashMap<NativeBinarySpec, VisualStudioProjectConfiguration>();

    public DefaultVisualStudioProject(ComponentSpecIdentifier componentIdentifier, NativeComponentSpec component, PathToFileResolver fileResolver, Instantiator instantiator) {
        super(componentIdentifier, VisualStudioProject.class);
        this.component = component;
        projectFile = instantiator.newInstance(DefaultConfigFile.class, fileResolver, getName() + ".vcxproj");
        filtersFile = instantiator.newInstance(DefaultConfigFile.class, fileResolver, getName() + ".vcxproj.filters");
    }

    public DefaultConfigFile getProjectFile() {
        return projectFile;
    }

    public DefaultConfigFile getFiltersFile() {
        return filtersFile;
    }

    public NativeComponentSpec getComponent() {
        return component;
    }

    public void addSourceFile(File sourceFile) {
        additionalFiles.add(sourceFile);
    }

    public String getUuid() {
        final String projectPath = component.getProjectPath();
        String vsComponentPath = projectPath + ":" + getName();
        return "{" + UUID.nameUUIDFromBytes(vsComponentPath.getBytes()).toString().toUpperCase() + "}";
    }

    public void source(Collection<LanguageSourceSet> sources) {
        this.sources.addAll(sources);
        builtBy(sources);
    }

    public Set<LanguageSourceSet> getSources() {
        return sources;
    }

    public List<File> getSourceFiles() {
        Set<File> allSource = new LinkedHashSet<File>();
        allSource.addAll(additionalFiles);

        for(LanguageSourceSet sourceSet : sources) {
            if (!(sourceSet instanceof WindowsResourceSet)) {
                allSource.addAll(sourceSet.getSource().getFiles());
            }
        }

        return new ArrayList<File>(allSource);
    }

    public List<File> getResourceFiles() {
        Set<File> allResources = new LinkedHashSet<File>();

        for(LanguageSourceSet sourceSet : sources) {
            if (sourceSet instanceof WindowsResourceSet) {
                allResources.addAll(sourceSet.getSource().getFiles());
            }
        }
        return new ArrayList<File>(allResources);
    }

    public List<File> getHeaderFiles() {
        Set<File> allHeaders = new LinkedHashSet<File>();

        for(LanguageSourceSet sourceSet : sources) {
            if (sourceSet instanceof HeaderExportingSourceSet) {
                HeaderExportingSourceSet exportingSourceSet = (HeaderExportingSourceSet) sourceSet;
                allHeaders.addAll(exportingSourceSet.getExportedHeaders().getFiles());
                allHeaders.addAll(exportingSourceSet.getImplicitHeaders().getFiles());
            }
        }

        return new ArrayList<File>(allHeaders);
    }

    public List<VisualStudioProjectConfiguration> getConfigurations() {
        return CollectionUtils.toList(configurations.values());
    }

    public void addConfiguration(NativeBinarySpec nativeBinary, VisualStudioProjectConfiguration configuration) {
        configurations.put(nativeBinary, configuration);
        NativeBinarySpecInternal specInternal = (NativeBinarySpecInternal) nativeBinary;
        source(specInternal.getInputs());
    }

    public VisualStudioProjectConfiguration getConfiguration(NativeBinarySpec nativeBinary) {
        return configurations.get(nativeBinary);
    }

    public static class DefaultConfigFile implements XmlConfigFile {
        private final List<Action<? super XmlProvider>> actions = new ArrayList<Action<? super XmlProvider>>();
        private final PathToFileResolver fileResolver;
        private Object location;

        public DefaultConfigFile(PathToFileResolver fileResolver, String defaultLocation) {
            this.fileResolver = fileResolver;
            this.location = defaultLocation;
        }

        public File getLocation() {
            return fileResolver.resolve(location);
        }

        public void setLocation(Object location) {
            this.location = location;
        }

        public void withXml(Action<? super XmlProvider> action) {
            actions.add(action);
        }

        public List<Action<? super XmlProvider>> getXmlActions() {
            return actions;
        }
    }
}
