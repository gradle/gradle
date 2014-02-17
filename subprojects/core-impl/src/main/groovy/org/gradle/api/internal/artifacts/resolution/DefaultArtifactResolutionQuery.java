/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.resolution;

import com.google.common.collect.*;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.resolution.*;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.specs.Specs;

import java.util.*;

public class DefaultArtifactResolutionQuery implements ArtifactResolutionQuery {
    private final ConfigurationContainer configurationContainer;

    private Set<ModuleComponentIdentifier> componentIds = Sets.newHashSet();
    private Class<? extends SoftwareComponent<?>> componentType;
    private Set<Class<? extends SoftwareArtifact>> artifactTypes = Sets.newHashSet();

    public DefaultArtifactResolutionQuery(ConfigurationContainer configurationContainer) {
        this.configurationContainer = configurationContainer;
    }

    public ArtifactResolutionQuery forComponent(String group, String module, String version) {
        componentIds.add(new DefaultModuleComponentIdentifier(group, module, version));
        return this;
    }

    public ArtifactResolutionQuery forComponents(Set<ModuleComponentIdentifier> componentIds) {
        this.componentIds.addAll(componentIds);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends SoftwareArtifact, U extends SoftwareComponent<T>> ArtifactResolutionQuery withArtifacts(Class<U> componentType, Class<T>... artifactTypes) {
        this.componentType = componentType;
        if (artifactTypes.length == 0) {
            this.artifactTypes = (Set) Sets.newHashSet(JvmLibrarySourcesArtifact.class, JvmLibraryJavadocArtifact.class);
        } else {
            this.artifactTypes.addAll(Arrays.asList(artifactTypes));
        }
        return this;
    }

    public ArtifactResolutionQueryResult execute() {
        final List<Dependency> artifactDependencies = createArtifactDependencies();
        Configuration configuration = configurationContainer.detachedConfiguration(
                artifactDependencies.toArray(new Dependency[artifactDependencies.size()]));

        Multimap<ComponentIdentifier, JvmLibraryArtifact> jvmLibraryArtifacts = ArrayListMultimap.create();
        LenientConfiguration lenientConfiguration = configuration.getResolvedConfiguration().getLenientConfiguration();
        Set<ResolvedArtifact> resolvedArtifacts = lenientConfiguration.getArtifacts(Specs.satisfyAll());
        // TODO: handle resolution failures (lenientConfiguration.getUnresolvedModuleDependencies)

        for (ResolvedArtifact artifact : resolvedArtifacts) {
            ModuleComponentIdentifier componentId = toComponentIdentifier(artifact.getModuleVersion().getId());
            jvmLibraryArtifacts.put(componentId, toJvmLibraryArtifact(artifact));
        }

        Set<JvmLibrary> jvmLibraries = Sets.newHashSet();
        for (Map.Entry<ComponentIdentifier, Collection<JvmLibraryArtifact>> entry : jvmLibraryArtifacts.asMap().entrySet()) {
            jvmLibraries.add(new DefaultJvmLibrary(entry.getKey(), ImmutableList.copyOf(entry.getValue())));
        }

        return new DefaultArtifactResolutionQueryResult(jvmLibraries);
    }

    private List<Dependency> createArtifactDependencies() {
        List<Dependency> dependencies = Lists.newArrayList();
        for (ModuleComponentIdentifier componentId : componentIds) {
            ExternalModuleDependency dependency = new DefaultExternalModuleDependency(componentId.getGroup(), componentId.getModule(), componentId.getVersion());
            dependency.setTransitive(false);
            if (artifactTypes.contains(JvmLibrarySourcesArtifact.class)) {
                dependency.addArtifact(new DefaultDependencyArtifact(dependency.getName(), "source", "jar", "sources", null));
            }
            if (artifactTypes.contains(JvmLibraryJavadocArtifact.class)) {
                dependency.addArtifact(new DefaultDependencyArtifact(dependency.getName(), "javadoc", "jar", "javadoc", null));
            }
            dependencies.add(dependency);
        }
        return dependencies;
    }

    private ModuleComponentIdentifier toComponentIdentifier(ModuleVersionIdentifier moduleId) {
        return new DefaultModuleComponentIdentifier(moduleId.getGroup(), moduleId.getName(), moduleId.getVersion());
    }

    private JvmLibraryArtifact toJvmLibraryArtifact(ResolvedArtifact artifact) {
        return artifact.getClassifier().equals("sources")
                ? new DefaultJvmLibrarySourcesArtifact(artifact.getFile())
                : new DefaultJvmLibraryJavadocArtifact(artifact.getFile());
    }
}
