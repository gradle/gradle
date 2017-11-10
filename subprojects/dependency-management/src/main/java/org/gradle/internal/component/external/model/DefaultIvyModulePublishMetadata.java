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

package org.gradle.internal.component.external.model;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.configurations.OutgoingVariant;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.local.model.BuildableLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultIvyModulePublishMetadata implements BuildableIvyModulePublishMetadata, BuildableLocalComponentMetadata {
    private static final Transformer<String, String> VERSION_TRANSFORMER = new IvyVersionTransformer();
    private final ModuleComponentIdentifier id;
    private final String status;
    private final Map<ModuleComponentArtifactIdentifier, IvyModuleArtifactPublishMetadata> artifactsById = new LinkedHashMap<ModuleComponentArtifactIdentifier, IvyModuleArtifactPublishMetadata>();
    private final Map<String, Configuration> configurations = new LinkedHashMap<String, Configuration>();
    private final Set<LocalOriginDependencyMetadata> dependencies = new LinkedHashSet<LocalOriginDependencyMetadata>();
    private final List<Exclude> excludes = new ArrayList<Exclude>();

    public DefaultIvyModulePublishMetadata(ModuleComponentIdentifier id, String status) {
        this.id = id;
        this.status = status;
    }

    public DefaultIvyModulePublishMetadata(IvyModulePublishMetadata metadata) {
        this.id = metadata.getId();
        this.status = metadata.getStatus();
    }

    public ModuleComponentIdentifier getId() {
        return id;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public Map<String, Configuration> getConfigurations() {
        return configurations;
    }

    @Override
    public Collection<LocalOriginDependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public Collection<Exclude> getExcludes() {
        return excludes;
    }

    @Override
    public void addConfiguration(String name, String description, Set<String> extendsFrom, Set<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, boolean canBeResolved) {
        List<String> sortedExtends = Lists.newArrayList(extendsFrom);
        Collections.sort(sortedExtends);
        Configuration configuration = new Configuration(name, transitive, visible, sortedExtends);
        configurations.put(name, configuration);
    }

    @Override
    public void addExclude(Exclude exclude) {
        excludes.add(exclude);
    }

    @Override
    public void addDependency(LocalOriginDependencyMetadata dependency) {
        dependencies.add(normalizeVersionForIvy(dependency));
    }

    /**
     * [1.0] is a valid version in maven, but not in Ivy: strip the surrounding '[' and ']' characters for ivy publish.
     */
    private static LocalOriginDependencyMetadata normalizeVersionForIvy(LocalOriginDependencyMetadata dependency) {
        if (dependency.getSelector() instanceof ModuleComponentSelector) {
            ModuleComponentSelector selector = (ModuleComponentSelector) dependency.getSelector();
            VersionConstraint versionConstraint = selector.getVersionConstraint();
            DefaultImmutableVersionConstraint transformedConstraint =
                new DefaultImmutableVersionConstraint(
                    VERSION_TRANSFORMER.transform(versionConstraint.getPreferredVersion()),
                    CollectionUtils.collect(versionConstraint.getRejectedVersions(), VERSION_TRANSFORMER));
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getGroup(), selector.getModule(), transformedConstraint);
            return dependency.withTarget(newSelector);
        }
        return dependency;
    }

    @Override
    public void addArtifacts(String configuration, Iterable<? extends PublishArtifact> artifacts) {
        for (PublishArtifact artifact : artifacts) {
            DefaultIvyArtifactName ivyName = DefaultIvyArtifactName.forPublishArtifact(artifact);
            DefaultIvyModuleArtifactPublishMetadata ivyArtifact = getOrCreate(ivyName);
            ivyArtifact.setFile(artifact.getFile());
            ivyArtifact.addConfiguration(configuration);
        }
    }

    @Override
    public void addVariant(String configuration, OutgoingVariant variant) {
        // Ignore
    }

    public void addArtifact(IvyArtifactName artifact, File file) {
        DefaultIvyModuleArtifactPublishMetadata publishMetadata = new DefaultIvyModuleArtifactPublishMetadata(id, artifact);
        publishMetadata.setFile(file);
        artifactsById.put(publishMetadata.getId(), publishMetadata);
    }

    public void addArtifact(IvyModuleArtifactPublishMetadata artifact) {
        artifactsById.put(artifact.getId(), artifact);
    }

    private DefaultIvyModuleArtifactPublishMetadata getOrCreate(IvyArtifactName ivyName) {
        for (IvyModuleArtifactPublishMetadata artifactPublishMetadata : artifactsById.values()) {
            if (artifactPublishMetadata.getArtifactName().equals(ivyName)) {
                return (DefaultIvyModuleArtifactPublishMetadata) artifactPublishMetadata;
            }
        }
        DefaultIvyModuleArtifactPublishMetadata artifact = new DefaultIvyModuleArtifactPublishMetadata(id, ivyName);
        artifactsById.put(artifact.getId(), artifact);
        return artifact;
    }

    public Collection<IvyModuleArtifactPublishMetadata> getArtifacts() {
        return artifactsById.values();
    }

    @Override
    public void addFiles(String configuration, LocalFileDependencyMetadata files) {
        // Ignore
    }

    private static class IvyVersionTransformer implements Transformer<String, String> {
        @Override
        public String transform(String version) {
            if (version != null && version.startsWith("[") && version.endsWith("]") && version.indexOf(',') == -1) {
                return version.substring(1, version.length() - 1);
            }
            return version;
        }
    }
}
