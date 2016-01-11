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

import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.UsageKind;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JarFile;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;

import java.util.Collections;
import java.util.EnumMap;

import static org.gradle.jvm.internal.DefaultJvmBinarySpec.collectDependencies;
import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData.newResolvedLibraryMetadata;

public class JvmLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {

    @Override
    @SuppressWarnings("unchecked")
    public DefaultLibraryLocalComponentMetaData createLocalComponentMetaData(BinarySpec selectedBinary, String projectPath) {
        JarBinarySpecInternal jarBinarySpec = (JarBinarySpecInternal) selectedBinary;
        EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage = new EnumMap<UsageKind, Iterable<DependencySpec>>(UsageKind.class);
        EnumMap<UsageKind, TaskDependency> buildDependenciesPerUsage = new EnumMap<UsageKind, TaskDependency>(UsageKind.class);

        JarFile apiJar = jarBinarySpec.getApiJar();
        populateUsageMetadata(UsageKind.API,
            apiJar,
            jarBinarySpec.getApiDependencies(),
            dependenciesPerUsage,
            buildDependenciesPerUsage);

        JarFile runtimeJar = jarBinarySpec.getRuntimeJar();
        JvmLibrarySpec library = jarBinarySpec.getLibrary();
        populateUsageMetadata(UsageKind.RUNTIME,
            runtimeJar,
            library != null ? collectDependencies(jarBinarySpec, library, library.getDependencies().getDependencies(), jarBinarySpec.getApiDependencies()) : Collections.<DependencySpec>emptyList(),
            dependenciesPerUsage,
            buildDependenciesPerUsage);

        DefaultLibraryLocalComponentMetaData metadata = newResolvedLibraryMetadata(jarBinarySpec.getId(), buildDependenciesPerUsage, dependenciesPerUsage, projectPath);
        addArtifact(UsageKind.API, apiJar, metadata);
        addArtifact(UsageKind.RUNTIME, runtimeJar, metadata);

        return metadata;
    }

    private static void populateUsageMetadata(UsageKind usage,
                                              JarFile artifact,
                                              Iterable<DependencySpec> dependencies,
                                              EnumMap<UsageKind, Iterable<DependencySpec>> dependenciesPerUsage,
                                              EnumMap<UsageKind, TaskDependency> buildDependenciesPerUsage) {
        DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
        buildDependencies.add(artifact);
        dependenciesPerUsage.put(usage, dependencies);
        buildDependenciesPerUsage.put(usage, buildDependencies);
    }

    private static void addArtifact(UsageKind usage, JarFile jarFile, DefaultLibraryLocalComponentMetaData metadata) {
        metadata.addArtifacts(usage.getConfigurationName(), Collections.singletonList(new LibraryPublishArtifact("jar", jarFile.getFile())));
    }
}
