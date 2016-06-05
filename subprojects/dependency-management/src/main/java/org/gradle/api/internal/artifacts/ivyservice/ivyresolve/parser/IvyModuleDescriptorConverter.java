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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.descriptor.Dependency;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
    public static ModuleDescriptorState forIvyModuleDescriptor(ModuleDescriptor ivyDescriptor) {
        ModuleRevisionId moduleRevisionId = ivyDescriptor.getModuleRevisionId();
        ModuleComponentIdentifier componentIdentifier = DefaultModuleComponentIdentifier.newId(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
        MutableModuleDescriptorState state = new MutableModuleDescriptorState(componentIdentifier, ivyDescriptor.getStatus(), ivyDescriptor.isDefault());

        state.setBranch(moduleRevisionId.getBranch());
        state.setDescription(ivyDescriptor.getDescription());
        state.setPublicationDate(ivyDescriptor.getPublicationDate());
        Map<NamespaceId, String> extraInfo = Cast.uncheckedCast(ivyDescriptor.getExtraInfo());
        state.getExtraInfo().putAll(extraInfo);

        for (org.apache.ivy.core.module.descriptor.Configuration ivyConfiguration : ivyDescriptor.getConfigurations()) {
            addConfiguration(state, ivyConfiguration);
        }
        for (ExcludeRule excludeRule : ivyDescriptor.getAllExcludeRules()) {
            addExcludeRule(state, excludeRule);
        }
        for (DependencyDescriptor dependencyDescriptor : ivyDescriptor.getDependencies()) {
            addDependency(state, dependencyDescriptor);
        }

        return state;
    }

    private static void addConfiguration(MutableModuleDescriptorState state, Configuration configuration) {
        String name = configuration.getName();
        boolean transitive = configuration.isTransitive();
        boolean visible = configuration.getVisibility() == org.apache.ivy.core.module.descriptor.Configuration.Visibility.PUBLIC;
        List<String> extendsFrom = Lists.newArrayList(configuration.getExtends());
        state.addConfiguration(name, transitive, visible, extendsFrom);
    }

    private static void addExcludeRule(MutableModuleDescriptorState state, ExcludeRule excludeRule) {
        state.addExclude(forIvyExclude(excludeRule));
    }

    private static void addDependency(MutableModuleDescriptorState state, DependencyDescriptor dependencyDescriptor) {
        ModuleRevisionId revisionId = dependencyDescriptor.getDependencyRevisionId();
        ModuleVersionSelector requested = DefaultModuleVersionSelector.newSelector(revisionId.getOrganisation(), revisionId.getName(), revisionId.getRevision());

        Dependency dep = state.addDependency(
            requested,
            dependencyDescriptor.getDynamicConstraintDependencyRevisionId().getRevision(),
            false,
            dependencyDescriptor.isChanging(),
            dependencyDescriptor.isTransitive());

        Map<String, List<String>> configMappings = readConfigMappings(dependencyDescriptor);
        for (String from : configMappings.keySet()) {
            for (String to : configMappings.get(from)) {
                dep.addDependencyConfiguration(from, to);
            }
        }

        for (DependencyArtifactDescriptor ivyArtifact : dependencyDescriptor.getAllDependencyArtifacts()) {
            IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(ivyArtifact.getName(), ivyArtifact.getType(), ivyArtifact.getExt(), (String) ivyArtifact.getExtraAttributes().get(CLASSIFIER));
            dep.addArtifact(ivyArtifactName, Sets.newHashSet(ivyArtifact.getConfigurations()));
        }

        for (ExcludeRule excludeRule : dependencyDescriptor.getAllExcludeRules()) {
            dep.addExcludeRule(forIvyExclude(excludeRule));
        }
    }

    private static Exclude forIvyExclude(org.apache.ivy.core.module.descriptor.ExcludeRule excludeRule) {
        ArtifactId id = excludeRule.getId();
        return new DefaultExclude(
            id.getModuleId().getOrganisation(), id.getModuleId().getName(), id.getName(), id.getType(), id.getExt(),
            excludeRule.getConfigurations(), excludeRule.getMatcher().getName());
    }

    // TODO We should get rid of this reflection (will need to reimplement the parser to create a ModuleDescriptorState directly)
    private static Map<String, List<String>> readConfigMappings(DependencyDescriptor dependencyDescriptor) {
        if (dependencyDescriptor instanceof DefaultDependencyDescriptor) {
            try {
                return (Map<String, List<String>>) DEPENDENCY_CONFIG_FIELD.get(dependencyDescriptor);
            } catch (IllegalAccessException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        String[] modConfs = dependencyDescriptor.getModuleConfigurations();
        Map<String, List<String>> results = Maps.newLinkedHashMap();
        for (String modConf : modConfs) {
            results.put(modConf, Arrays.asList(dependencyDescriptor.getDependencyConfigurations(modConfs)));
        }
        return results;
    }

}
