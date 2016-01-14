/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.resolve;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.UsageKind;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JarFile;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.io.File;
import java.util.*;

import static org.gradle.jvm.internal.DefaultJvmBinarySpec.collectDependencies;
import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData.newResolvedLibraryMetadata;

public class JvmLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {

    @Override
    @SuppressWarnings("unchecked")
    public DefaultLibraryLocalComponentMetaData createLocalComponentMetaData(BinarySpec selectedBinary, String projectPath, boolean toAssembly) {
        EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage = new EnumMap<UsageKind, Iterable<DependencySpec>>(UsageKind.class);
        EnumMap<UsageKind, TaskDependency> buildDependenciesPerUsage = new EnumMap<UsageKind, TaskDependency>(UsageKind.class);
        EnumMap<UsageKind, List<PublishArtifact>> artifacts = new EnumMap<UsageKind, List<PublishArtifact>>(UsageKind.class);
        initializeUsages(dependenciesPerUsage, buildDependenciesPerUsage, artifacts);
        if (selectedBinary instanceof JarBinarySpecInternal) {
            JarBinarySpecInternal jarBinarySpec = (JarBinarySpecInternal) selectedBinary;
            createJarBinarySpecLocalComponentMetaData(artifacts, jarBinarySpec, dependenciesPerUsage, buildDependenciesPerUsage, toAssembly);
        }
        if (selectedBinary instanceof WithJvmAssembly) {
            // a local component that provides a JVM assembly
            JvmAssembly assembly = ((WithJvmAssembly) selectedBinary).getAssembly();
            createJvmAssemblyLocalComponentMetaData(artifacts, assembly, dependenciesPerUsage, buildDependenciesPerUsage, toAssembly);
        }
        return createResolvedMetaData((BinarySpecInternal) selectedBinary, projectPath, dependenciesPerUsage, buildDependenciesPerUsage, artifacts);
    }

    private DefaultLibraryLocalComponentMetaData createResolvedMetaData(BinarySpecInternal selectedBinary, String projectPath, EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage, EnumMap<UsageKind, TaskDependency> buildDependenciesPerUsage, EnumMap<UsageKind, List<PublishArtifact>> artifacts) {
        DefaultLibraryLocalComponentMetaData metadata = newResolvedLibraryMetadata(selectedBinary.getId(), buildDependenciesPerUsage, dependenciesPerUsage, projectPath);
        for (Map.Entry<UsageKind, List<PublishArtifact>> entry : artifacts.entrySet()) {
            UsageKind usage = entry.getKey();
            List<PublishArtifact> publishArtifacts = entry.getValue();
            metadata.addArtifacts(usage.getConfigurationName(), publishArtifacts);
        }
        return metadata;
    }

    private void initializeUsages(EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage, EnumMap<UsageKind, TaskDependency> buildDependenciesPerUsage, EnumMap<UsageKind, List<PublishArtifact>> artifacts) {
        for (UsageKind usageKind : UsageKind.values()) {
            dependenciesPerUsage.put(usageKind, Collections.<DependencySpec>emptyList());
            buildDependenciesPerUsage.put(usageKind, new DefaultTaskDependency());
            artifacts.put(usageKind, new LinkedList<PublishArtifact>());
        }
    }

    @SuppressWarnings("unchecked")
    private void createJarBinarySpecLocalComponentMetaData(EnumMap<UsageKind, List<PublishArtifact>> artifacts, JarBinarySpecInternal jarBinarySpec, EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage, EnumMap<UsageKind, TaskDependency> buildDependenciesPerUsage, boolean toAssembly) {
        JarFile apiJar = jarBinarySpec.getApiJar();
        configureUsageMetadata(UsageKind.API,
            toAssembly ? null : apiJar,
            jarBinarySpec.getApiDependencies(),
            dependenciesPerUsage,
            buildDependenciesPerUsage);

        JarFile runtimeJar = jarBinarySpec.getRuntimeJar();
        JvmLibrarySpec library = jarBinarySpec.getLibrary();
        configureUsageMetadata(UsageKind.RUNTIME,
            toAssembly ? null : runtimeJar,
            library != null ? collectDependencies(jarBinarySpec, library, library.getDependencies().getDependencies(), jarBinarySpec.getApiDependencies()) : Collections.<DependencySpec>emptyList(),
            dependenciesPerUsage,
            buildDependenciesPerUsage);

        if (!toAssembly) {
            addArtifact(UsageKind.API, apiJar, artifacts);
            addArtifact(UsageKind.RUNTIME, runtimeJar, artifacts);
        }

    }

    private void createJvmAssemblyLocalComponentMetaData(EnumMap<UsageKind, List<PublishArtifact>> artifacts, JvmAssembly assembly, EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage, EnumMap<UsageKind, TaskDependency> buildDependenciesPerUsage, boolean toAssembly) {
        configureUsageMetadata(UsageKind.API,
            toAssembly ? assembly : null,
            Collections.<DependencySpec>emptyList(),
            dependenciesPerUsage,
            buildDependenciesPerUsage);
        configureUsageMetadata(UsageKind.RUNTIME,
            toAssembly ? assembly : null,
            Collections.<DependencySpec>emptyList(),
            dependenciesPerUsage,
            buildDependenciesPerUsage);
        if (toAssembly) {
            // TODO:Cedric This is an approximation: when a component wants to compile against the assembly of
            // a library (not the jar), then we should give it the *stubbed classes* instead of the raw classes. However:
            // - there's no such thing as a "stubbed classes assembly"
            // - for performance reasons only the classes that belong to the API are stubbed, so we would miss the classes that do not belong to the API
            // So this makes the UsageKind.API misleading (should this be COMPILE?).
            addArtifact(UsageKind.API, assembly.getClassDirectories(), artifacts);
            addArtifact(UsageKind.RUNTIME, Sets.union(assembly.getClassDirectories(), assembly.getResourceDirectories()), artifacts);
        }
    }

    private static void configureUsageMetadata(UsageKind usage,
                                               Object buildDependency,
                                               Iterable<DependencySpec> dependencies,
                                               EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage,
                                               EnumMap<UsageKind, TaskDependency> buildDependenciesPerUsage) {
        Iterable<DependencySpec> dependencySpecs = dependenciesPerUsage.get(usage);
        dependenciesPerUsage.put(usage, Iterables.concat(dependencies, dependencySpecs));
        if (buildDependency !=null) {
            DefaultTaskDependency buildDependencies = (DefaultTaskDependency) buildDependenciesPerUsage.get(usage);
            buildDependencies.add(buildDependency);
        }
    }

    private static void addArtifact(UsageKind usage, JarFile jarFile, EnumMap<UsageKind, List<PublishArtifact>> artifacts) {
        artifacts.get(usage).add(new LibraryPublishArtifact("jar", jarFile.getFile()));
    }

    private static void addArtifact(UsageKind usage, Set<File> directories, EnumMap<UsageKind, List<PublishArtifact>> artifacts) {
        List<PublishArtifact> publishArtifacts = artifacts.get(usage);
        for (File dir : directories) {
            publishArtifacts.add(new DefaultPublishArtifact("assembly", "", "", "", new Date(dir.lastModified()), dir));
        }
    }

}
