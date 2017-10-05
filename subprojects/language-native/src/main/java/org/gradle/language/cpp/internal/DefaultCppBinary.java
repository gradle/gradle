/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp.internal;

import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.nativeplatform.internal.Names;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultCppBinary implements CppBinary {
    private final String name;
    private final Provider<String> baseName;
    private final boolean debuggable;
    private final FileCollection sourceFiles;
    private final FileCollection includePath;
    private final FileCollection linkLibraries;
    private final FileCollection runtimeLibraries;

    public DefaultCppBinary(String name, ObjectFactory objects, Provider<String> baseName, boolean debuggable, FileCollection sourceFiles, FileCollection componentHeaderDirs, ConfigurationContainer configurations, Configuration implementation) {
        this.name = name;
        this.baseName = baseName;
        this.debuggable = debuggable;
        this.sourceFiles = sourceFiles;

        Names names = Names.of(name);

        // TODO - reduce duplication with Swift binary
        Configuration includePathConfig = configurations.maybeCreate(names.withPrefix("cppCompile"));
        includePathConfig.setCanBeConsumed(false);
        includePathConfig.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.C_PLUS_PLUS_API));
        includePathConfig.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debuggable);

        Configuration nativeLink = configurations.maybeCreate(names.withPrefix("nativeLink"));
        nativeLink.setCanBeConsumed(false);
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.NATIVE_LINK));
        nativeLink.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debuggable);

        Configuration nativeRuntime = configurations.maybeCreate(names.withPrefix("nativeRuntime"));
        nativeRuntime.setCanBeConsumed(false);
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.NATIVE_RUNTIME));
        nativeRuntime.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debuggable);

        includePathConfig.extendsFrom(implementation);
        nativeLink.extendsFrom(implementation);
        nativeRuntime.extendsFrom(implementation);

        includePath = componentHeaderDirs.plus(new FileCollectionAdapter(new IncludePath(includePathConfig)));
        linkLibraries = new FileCollectionAdapter(new LinkLibs(nativeLink, configurations));
        runtimeLibraries = new FileCollectionAdapter(new RuntimeLibs(nativeRuntime, configurations));
    }

    @Inject
    protected FileOperations getFileOperations() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected TemporaryFileProvider getTemporaryFileProvider() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected NativeDependencyCache getNativeDependencyCache() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Provider<String> getBaseName() {
        return baseName;
    }

    @Override
    public boolean isDebuggable() {
        return debuggable;
    }

    @Override
    public FileCollection getCppSource() {
        return sourceFiles;
    }

    @Override
    public FileCollection getCompileIncludePath() {
        return includePath;
    }

    @Override
    public FileCollection getLinkLibraries() {
        return linkLibraries;
    }

    @Override
    public FileCollection getRuntimeLibraries() {
        return runtimeLibraries;
    }

    private class IncludePath implements MinimalFileSet {
        private final Configuration includePathConfig;
        private Set<File> result;

        IncludePath(Configuration includePathConfig) {
            this.includePathConfig = includePathConfig;
        }

        @Override
        public String getDisplayName() {
            return "Include path for " + DefaultCppBinary.this.toString();
        }

        @Override
        public Set<File> getFiles() {
            if (result == null) {
                // All this is intended to go away as more Gradle-specific metadata is included in the publications and the dependency resolution engine can just figure this stuff out for us
                // This is intentionally dumb and will improve later

                // Collect the files from anything other than an external component, use these directly in the result
                // for external components, unzip the headers into a cache (if not already present)
                ArtifactCollection artifacts = includePathConfig.getIncoming().getArtifacts();
                Set<File> files = new LinkedHashSet<File>();
                if (!artifacts.getArtifacts().isEmpty()) {
                    NativeDependencyCache cache = getNativeDependencyCache();
                    for (ResolvedArtifactResult artifact : artifacts) {
                        if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier) {
                            // Unzip the headers into cache
                            ModuleComponentIdentifier id = (ModuleComponentIdentifier) artifact.getId().getComponentIdentifier();
                            File headerDir = cache.getUnpackedHeaders(artifact.getFile(), id.getModule() + "-" + id.getVersion());
                            files.add(headerDir);
                        } else {
                            files.add(artifact.getFile());
                        }
                    }
                }
                result = files;
            }
            return result;
        }
    }

    private class LinkLibs implements MinimalFileSet, Buildable {
        private final ConfigurationContainer configurations;
        private final Configuration configuration;
        private Set<File> result;

        LinkLibs(Configuration configuration, ConfigurationContainer configurations) {
            this.configuration = configuration;
            this.configurations = configurations;
        }

        @Override
        public String getDisplayName() {
            return "Link libraries of " + DefaultCppBinary.this;
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return configuration.getBuildDependencies();
        }

        @Override
        public Set<File> getFiles() {
            if (result == null) {
                // All this is intended to go away as more Gradle-specific metadata is included in the publications and the dependency resolution engine can just figure this stuff out for us

                // Collect up the external components in the result to resolve again to get the link artifact
                configuration.getResolvedConfiguration().rethrowFailure();
                Set<ResolvedComponentResult> components = configuration.getIncoming().getResolutionResult().getAllComponents();
                List<Dependency> externalDependencies = new ArrayList<Dependency>(components.size());
                for (ResolvedComponentResult component : components) {
                    if (component.getId() instanceof ModuleComponentIdentifier) {
                        ModuleComponentIdentifier id = (ModuleComponentIdentifier) component.getId();
                        // TODO - use the correct variant
                        String module = id.getModule() + "_debug";
                        // TODO - use naming scheme for target platform
                        DefaultExternalModuleDependency mappedDependency = new DefaultExternalModuleDependency(id.getGroup(), module, id.getVersion());
                        mappedDependency.setTransitive(false);
                        externalDependencies.add(mappedDependency);
                    }
                }

                // Collect the files from anything other than an external component, use these directly in the result
                ArtifactCollection artifacts = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
                    @Override
                    public void execute(ArtifactView.ViewConfiguration viewConfiguration) {
                        viewConfiguration.componentFilter(new Spec<ComponentIdentifier>() {
                            @Override
                            public boolean isSatisfiedBy(ComponentIdentifier element) {
                                return !(element instanceof ModuleComponentIdentifier);
                            }
                        });
                    }
                }).getArtifacts();
                Set<File> files = new LinkedHashSet<File>();
                for (ResolvedArtifactResult artifact : artifacts) {
                    files.add(artifact.getFile());
                }

                // This is intentionally dumb and will improve later
                // Conflict resolution isn't applied to implementation dependencies
                // The files of the result are not ordered as they would be if the original configuration is resolved
                // This is also broken when a runtime dependency is satisfied by an included build
                if (!externalDependencies.isEmpty()) {
                    Configuration mappedConfiguration = configurations.detachedConfiguration(externalDependencies.toArray(new Dependency[0]));
                    mappedConfiguration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, configuration.getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE));

                    // Rename the downloaded file to the expected name for the binary
                    for (ResolvedArtifactResult artifact : mappedConfiguration.getIncoming().getArtifacts().getArtifacts()) {
                        ModuleComponentIdentifier id = (ModuleComponentIdentifier) artifact.getId().getComponentIdentifier();
                        String originalModuleName = id.getModule().substring(0, id.getModule().length() - "_debug".length());
                        String libName = getLibraryName(originalModuleName);
                        files.add(getNativeDependencyCache().getBinary(artifact.getFile(), libName));
                    }
                }

                result = files;
            }
            return result;
        }

        private String getLibraryName(String baseName) {
            // TODO - use naming scheme for target platform
            return OperatingSystem.current().getLinkLibraryName(baseName);
        }
    }

    private class RuntimeLibs implements MinimalFileSet, Buildable {
        private final ConfigurationContainer configurations;
        private final Configuration configuration;
        private Set<File> result;

        RuntimeLibs(Configuration configuration, ConfigurationContainer configurations) {
            this.configuration = configuration;
            this.configurations = configurations;
        }

        @Override
        public String getDisplayName() {
            return "Runtime libraries for " + DefaultCppBinary.this;
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return configuration.getBuildDependencies();
        }

        @Override
        public Set<File> getFiles() {
            if (result == null) {
                // All this is intended to go away as more Gradle-specific metadata is included in the publications and the dependency resolution engine can just figure this stuff out for us

                // Collect up the external components in the result to resolve again to get the link artifact
                configuration.getResolvedConfiguration().rethrowFailure();
                Set<ResolvedComponentResult> components = configuration.getIncoming().getResolutionResult().getAllComponents();
                List<Dependency> externalDependencies = new ArrayList<Dependency>(components.size());
                for (ResolvedComponentResult component : components) {
                    if (component.getId() instanceof ModuleComponentIdentifier) {
                        ModuleComponentIdentifier id = (ModuleComponentIdentifier) component.getId();
                        // TODO - use the correct variant
                        String module = id.getModule() + "_debug";
                        // TODO - use naming scheme for target platform
                        DefaultExternalModuleDependency mappedDependency = new DefaultExternalModuleDependency(id.getGroup(), module, id.getVersion());
                        externalDependencies.add(mappedDependency);
                    }
                }

                // Collect the files from anything other than an external component, use these directly in the result
                ArtifactCollection artifacts = configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
                    @Override
                    public void execute(ArtifactView.ViewConfiguration viewConfiguration) {
                        viewConfiguration.componentFilter(new Spec<ComponentIdentifier>() {
                            @Override
                            public boolean isSatisfiedBy(ComponentIdentifier element) {
                                return !(element instanceof ModuleComponentIdentifier);
                            }
                        });
                    }
                }).getArtifacts();
                Set<File> files = new LinkedHashSet<File>();
                for (ResolvedArtifactResult artifact : artifacts) {
                    files.add(artifact.getFile());
                }

                // This is intentionally dumb and will improve later
                // Conflict resolution isn't applied to implementation dependencies
                // The files of the result are not ordered as they would be if the original configuration is resolved
                // This is also broken when a runtime dependency is satisfied by an included build
                if (!externalDependencies.isEmpty()) {
                    Configuration mappedConfiguration = configurations.detachedConfiguration(externalDependencies.toArray(new Dependency[0]));
                    // Redirect transitive runtime dependencies
                    mappedConfiguration.getResolutionStrategy().eachDependency(new Action<DependencyResolveDetails>() {
                        @Override
                        public void execute(DependencyResolveDetails details) {
                            ModuleVersionSelector requested = details.getRequested();
                            if (!requested.getName().endsWith("_debug")) {
                                details.useTarget(requested.getGroup() + ":" + requested.getName() + "_debug:" + requested.getVersion());
                            }
                        }
                    });

                    mappedConfiguration.getResolvedConfiguration().rethrowFailure();
                    Set<ResolvedComponentResult> runtimeComponents = mappedConfiguration.getIncoming().getResolutionResult().getAllComponents();
                    List<Dependency> artifactDependencies = new ArrayList<Dependency>();
                    for (ResolvedComponentResult component : runtimeComponents) {
                        if (!(component.getId() instanceof ModuleComponentIdentifier)) {
                            continue;
                        }
                        ModuleComponentIdentifier id = (ModuleComponentIdentifier) component.getId();
                        DefaultExternalModuleDependency artifactDependency = new DefaultExternalModuleDependency(id.getGroup(), id.getModule(), id.getVersion());
                        artifactDependency.setTransitive(false);
                        artifactDependencies.add(artifactDependency);
                    }

                    mappedConfiguration = configurations.detachedConfiguration(artifactDependencies.toArray(new Dependency[0]));
                    mappedConfiguration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, configuration.getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE));

                    // Rename the downloaded file to the expected name for the binary
                    for (ResolvedArtifactResult artifact : mappedConfiguration.getIncoming().getArtifacts().getArtifacts()) {
                        ModuleComponentIdentifier id = (ModuleComponentIdentifier) artifact.getId().getComponentIdentifier();
                        String originalModuleName = id.getModule().substring(0, id.getModule().length() - "_debug".length());
                        String libName = getLibraryName(originalModuleName);
                        files.add(getNativeDependencyCache().getBinary(artifact.getFile(), libName));
                    }
                }

                result = files;
            }
            return result;
        }

        private String getLibraryName(String baseName) {
            // TODO - use naming scheme for target platform
            return OperatingSystem.current().getSharedLibraryName(baseName);
        }
    }
}
