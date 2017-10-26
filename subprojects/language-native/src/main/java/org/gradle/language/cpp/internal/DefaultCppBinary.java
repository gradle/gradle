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

import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.nativeplatform.internal.Names;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultCppBinary implements CppBinary {
    private final String name;
    private final Provider<String> baseName;
    private final boolean debuggable;
    private final FileCollection sourceFiles;
    private final FileCollection includePath;
    private final FileCollection linkLibraries;
    private final FileCollection runtimeLibraries;
    private final DirectoryProperty objectsDir;

    public DefaultCppBinary(String name, ProjectLayout projectLayout, ObjectFactory objects, Provider<String> baseName, boolean debuggable, FileCollection sourceFiles, FileCollection componentHeaderDirs, ConfigurationContainer configurations, Configuration implementation) {
        this.name = name;
        this.baseName = baseName;
        this.debuggable = debuggable;
        this.sourceFiles = sourceFiles;
        this.objectsDir = projectLayout.directoryProperty();

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
        linkLibraries = nativeLink;
        runtimeLibraries = nativeRuntime;
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

    public DirectoryProperty getObjectsDir() {
        return objectsDir;
    }

    @Override
    public FileCollection getObjects() {
        return objectsDir.getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o"));
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

                // Collect the files from anything other than an external component and use these directly in the result
                // For external components, unzip the headers into a cache, if not already present.
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
}
