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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IvyModuleDescriptorConverter {

    private static final String CLASSIFIER = "classifier";
    private static final Field DEPENDENCY_CONFIG_FIELD;
    static {
        try {
            DEPENDENCY_CONFIG_FIELD = DefaultDependencyDescriptor.class.getDeclaredField("confs");
            DEPENDENCY_CONFIG_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public IvyModuleDescriptorConverter(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    @SuppressWarnings("deprecation")
    public Map<NamespaceId, String> extractExtraAttributes(ModuleDescriptor ivyDescriptor) {
        Map<String, String> extraInfo = ivyDescriptor.getExtraInfo();
        return extraInfo.entrySet().stream().collect(
            Collectors.toMap(e -> NamespaceId.decode(e.getKey()), Map.Entry::getValue));
    }

    public List<Exclude> extractExcludes(ModuleDescriptor ivyDescriptor) {
        List<Exclude> result = new ArrayList<>(ivyDescriptor.getAllExcludeRules().length);
        for (ExcludeRule excludeRule : ivyDescriptor.getAllExcludeRules()) {
            result.add(forIvyExclude(excludeRule));
        }
        return result;
    }

    public List<IvyDependencyDescriptor> extractDependencies(ModuleDescriptor ivyDescriptor) {
        List<IvyDependencyDescriptor> result = new ArrayList<>(ivyDescriptor.getDependencies().length);
        for (DependencyDescriptor dependencyDescriptor : ivyDescriptor.getDependencies()) {
            addDependency(result, dependencyDescriptor);
        }
        return result;
    }

    public List<Configuration> extractConfigurations(ModuleDescriptor ivyDescriptor) {
        List<Configuration> result = new ArrayList<>(ivyDescriptor.getConfigurations().length);
        for (org.apache.ivy.core.module.descriptor.Configuration ivyConfiguration : ivyDescriptor.getConfigurations()) {
            addConfiguration(result, ivyConfiguration);
        }
        return result;
    }

    private static void addConfiguration(List<Configuration> result, org.apache.ivy.core.module.descriptor.Configuration configuration) {
        String name = configuration.getName();
        boolean transitive = configuration.isTransitive();
        boolean visible = configuration.getVisibility() == org.apache.ivy.core.module.descriptor.Configuration.Visibility.PUBLIC;
        List<String> extendsFrom = ImmutableList.copyOf(configuration.getExtends());
        result.add(new Configuration(name, transitive, visible, extendsFrom));
    }

    private void addDependency(List<IvyDependencyDescriptor> result, DependencyDescriptor dependencyDescriptor) {
        ModuleRevisionId revisionId = dependencyDescriptor.getDependencyRevisionId();
        ModuleComponentSelector requested = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(revisionId.getOrganisation(), revisionId.getName()), new DefaultImmutableVersionConstraint(revisionId.getRevision()));

        ListMultimap<String, String> configMappings = ArrayListMultimap.create();
        for (Map.Entry<String, List<String>> entry : readConfigMappings(dependencyDescriptor).entrySet()) {
            configMappings.putAll(entry.getKey(), entry.getValue());
        }

        List<Artifact> artifacts = new ArrayList<>();
        for (DependencyArtifactDescriptor ivyArtifact : dependencyDescriptor.getAllDependencyArtifacts()) {
            IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(ivyArtifact.getName(), ivyArtifact.getType(), ivyArtifact.getExt(), ivyArtifact.getExtraAttributes().get(CLASSIFIER));
            artifacts.add(new Artifact(ivyArtifactName, Sets.newHashSet(ivyArtifact.getConfigurations())));
        }

        List<Exclude> excludes = new ArrayList<>();
        for (ExcludeRule excludeRule : dependencyDescriptor.getAllExcludeRules()) {
            excludes.add(forIvyExclude(excludeRule));
        }

        result.add(new IvyDependencyDescriptor(
            requested,
            dependencyDescriptor.getDynamicConstraintDependencyRevisionId().getRevision(),
            dependencyDescriptor.isChanging(),
            dependencyDescriptor.isTransitive(),
            false,
            configMappings,
            artifacts,
            excludes));
    }

    private Exclude forIvyExclude(org.apache.ivy.core.module.descriptor.ExcludeRule excludeRule) {
        ArtifactId id = excludeRule.getId();
        IvyArtifactName artifactExclusion = artifactForIvyExclude(id);
        return new DefaultExclude(
            moduleIdentifierFactory.module(id.getModuleId().getOrganisation(), id.getModuleId().getName()), artifactExclusion, excludeRule.getConfigurations(), excludeRule.getMatcher().getName());
    }

    private IvyArtifactName artifactForIvyExclude(ArtifactId id) {
        if (PatternMatchers.ANY_EXPRESSION.equals(id.getName())
            && PatternMatchers.ANY_EXPRESSION.equals(id.getType())
            && PatternMatchers.ANY_EXPRESSION.equals(id.getExt())) {
            return null;
        }
        return new DefaultIvyArtifactName(id.getName(), id.getType(), id.getExt());
    }

    // TODO We should get rid of this reflection (will need to reimplement the parser to act on the metadata directly)
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> readConfigMappings(DependencyDescriptor dependencyDescriptor) {
        if (dependencyDescriptor instanceof DefaultDependencyDescriptor) {
            try {
                return (Map<String, List<String>>) DEPENDENCY_CONFIG_FIELD.get(dependencyDescriptor);
            } catch (IllegalAccessException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        String[] modConfs = dependencyDescriptor.getModuleConfigurations();
        Map<String, List<String>> results = new LinkedHashMap<>();
        for (String modConf : modConfs) {
            results.put(modConf, Arrays.asList(dependencyDescriptor.getDependencyConfigurations(modConfs)));
        }
        return results;
    }

}
