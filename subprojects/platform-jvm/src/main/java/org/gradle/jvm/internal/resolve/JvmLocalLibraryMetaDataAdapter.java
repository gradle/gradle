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

package org.gradle.jvm.internal.resolve;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.resolve.LocalLibraryMetaDataAdapter;
import org.gradle.internal.Describables;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JarFile;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata;
import org.gradle.platform.base.Binary;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.jvm.internal.DefaultJvmBinarySpec.collectDependencies;
import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata.newResolvedLibraryMetadata;

public class JvmLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {

    @Override
    @SuppressWarnings("unchecked")
    public DefaultLibraryLocalComponentMetadata createLocalComponentMetaData(Binary selectedBinary, String projectPath, boolean toAssembly) {
        EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage = new EnumMap<UsageKind, Iterable<DependencySpec>>(UsageKind.class);
        EnumMap<UsageKind, List<PublishArtifact>> artifacts = new EnumMap<UsageKind, List<PublishArtifact>>(UsageKind.class);
        initializeUsages(dependenciesPerUsage, artifacts);
        if (selectedBinary instanceof JarBinarySpecInternal) {
            JarBinarySpecInternal jarBinarySpec = (JarBinarySpecInternal) selectedBinary;
            createJarBinarySpecLocalComponentMetaData(artifacts, jarBinarySpec, dependenciesPerUsage, toAssembly);
        }
        if (selectedBinary instanceof WithJvmAssembly) {
            // a local component that provides a JVM assembly
            JvmAssembly assembly = ((WithJvmAssembly) selectedBinary).getAssembly();
            createJvmAssemblyLocalComponentMetaData(artifacts, assembly, dependenciesPerUsage, toAssembly);
        }
        return createResolvedMetaData((BinarySpecInternal) selectedBinary, projectPath, dependenciesPerUsage, artifacts);
    }

    private DefaultLibraryLocalComponentMetadata createResolvedMetaData(BinarySpecInternal selectedBinary, String projectPath, EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage, EnumMap<UsageKind, List<PublishArtifact>> artifacts) {

        final DefaultLibraryLocalComponentMetadata metadata = newResolvedLibraryMetadata(selectedBinary.getId(), toStringMap(dependenciesPerUsage), projectPath);
        for (Map.Entry<UsageKind, List<PublishArtifact>> entry : artifacts.entrySet()) {
            UsageKind usage = entry.getKey();
            List<PublishArtifact> publishArtifacts = entry.getValue();
            metadata.addArtifacts(usage.getConfigurationName(), publishArtifacts);
            metadata.addVariant(usage.getConfigurationName(), usage.getConfigurationName(), null, Describables.of(metadata.getId()), ImmutableAttributes.EMPTY, publishArtifacts);
        }
        return metadata;
    }

    private <T> Map<String, T> toStringMap(EnumMap<? extends Enum<UsageKind>, T> enumMap) {
        Map<String, T> map = new HashMap<String, T>(enumMap.size());
        for (Map.Entry<? extends Enum<UsageKind>, T> tEntry : enumMap.entrySet()) {
            UsageKind usageKind = UsageKind.valueOf(tEntry.getKey().name());
            map.put(usageKind.getConfigurationName(), tEntry.getValue());
        }
        return map;
    }

    private void initializeUsages(EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage, EnumMap<UsageKind, List<PublishArtifact>> artifacts) {
        for (UsageKind usageKind : UsageKind.values()) {
            dependenciesPerUsage.put(usageKind, Collections.<DependencySpec>emptyList());
            artifacts.put(usageKind, new LinkedList<PublishArtifact>());
        }
    }

    @SuppressWarnings("unchecked")
    private void createJarBinarySpecLocalComponentMetaData(EnumMap<UsageKind, List<PublishArtifact>> artifacts, JarBinarySpecInternal jarBinarySpec, EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage, boolean toAssembly) {
        JarFile apiJar = jarBinarySpec.getApiJar();
        configureUsageMetadata(UsageKind.API,
            jarBinarySpec.getApiDependencies(),
            dependenciesPerUsage);

        JarFile runtimeJar = jarBinarySpec.getRuntimeJar();
        JvmLibrarySpec library = jarBinarySpec.getLibrary();
        configureUsageMetadata(UsageKind.RUNTIME,
            library != null ? collectDependencies(jarBinarySpec, library, library.getDependencies().getDependencies(), jarBinarySpec.getApiDependencies()) : Collections.<DependencySpec>emptyList(),
            dependenciesPerUsage);

        if (!toAssembly) {
            addArtifact(UsageKind.API, apiJar, artifacts);
            addArtifact(UsageKind.RUNTIME, runtimeJar, artifacts);
        }

    }

    private void createJvmAssemblyLocalComponentMetaData(EnumMap<UsageKind, List<PublishArtifact>> artifacts, JvmAssembly assembly, EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage, boolean toAssembly) {
        configureUsageMetadata(UsageKind.API,
            Collections.<DependencySpec>emptyList(),
            dependenciesPerUsage);
        configureUsageMetadata(UsageKind.RUNTIME,
            Collections.<DependencySpec>emptyList(),
            dependenciesPerUsage);
        if (toAssembly) {
            // TODO:Cedric This is an approximation: when a component wants to compile against the assembly of
            // a library (not the jar), then we should give it the *stubbed classes* instead of the raw classes. However:
            // - there's no such thing as a "stubbed classes assembly"
            // - for performance reasons only the classes that belong to the API are stubbed, so we would miss the classes that do not belong to the API
            // So this makes the UsageKind.API misleading (should this be COMPILE?).
            addArtifact(UsageKind.API, assembly.getClassDirectories(), artifacts, assembly);
            addArtifact(UsageKind.RUNTIME, Sets.union(assembly.getClassDirectories(), assembly.getResourceDirectories()), artifacts, assembly);
        }
    }

    private static void configureUsageMetadata(UsageKind usage,
                                               Iterable<DependencySpec> dependencies,
                                               EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage) {
        Iterable<DependencySpec> dependencySpecs = dependenciesPerUsage.get(usage);
        dependenciesPerUsage.put(usage, Iterables.concat(dependencies, dependencySpecs));
    }

    private static void addArtifact(UsageKind usage, JarFile jarFile, EnumMap<UsageKind, List<PublishArtifact>> artifacts) {
        LibraryPublishArtifact publishArtifact = new LibraryPublishArtifact("jar", jarFile.getFile());
        publishArtifact.builtBy(jarFile);
        artifacts.get(usage).add(publishArtifact);
    }

    private static void addArtifact(UsageKind usage, Set<File> directories, EnumMap<UsageKind, List<PublishArtifact>> artifacts, JvmAssembly assembly) {
        List<PublishArtifact> publishArtifacts = artifacts.get(usage);
        for (File dir : directories) {
            DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact("assembly", "", "", "", new Date(dir.lastModified()), dir);
            publishArtifact.builtBy(assembly);
            publishArtifacts.add(publishArtifact);
        }
    }

}
