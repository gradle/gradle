/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Set;

public class DefaultExcludeFactory implements ExcludeFactory {
    @Override
    public ExcludeNothing nothing() {
        return DefaultExcludeNothing.get();
    }

    @Override
    public ExcludeEverything everything() {
        return DefaultExcludeEverything.get();
    }

    @Override
    public GroupExclude group(String group) {
        return DefaultGroupExclude.of(group);
    }

    @Override
    public ModuleExclude module(String module) {
        return DefaultModuleExclude.of(module);
    }

    @Override
    public ModuleIdExclude moduleId(ModuleIdentifier id) {
        return DefaultModuleIdExclude.of(id);
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return DefaultExcludeAnyOf.of(ImmutableSet.of(one, two));
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return DefaultExcludeAllOf.of(ImmutableSet.of(one, two));
    }

    @Override
    public ExcludeSpec anyOf(Set<ExcludeSpec> specs) {
        return DefaultExcludeAnyOf.of(ImmutableSet.copyOf(specs));
    }

    @Override
    public ExcludeSpec allOf(Set<ExcludeSpec> specs) {
        return DefaultExcludeAllOf.of(ImmutableSet.copyOf(specs));
    }

    @Override
    public ExcludeSpec ivyPatternExclude(ModuleIdentifier moduleId, IvyArtifactName artifact, String matcher) {
        return DefaultIvyPatternMatcherExcludeRuleSpec.of(moduleId, artifact, matcher);
    }

    @Override
    public ModuleIdSetExclude moduleIdSet(Set<ModuleIdentifier> modules) {
        return DefaultModuleIdSetExclude.of(modules);
    }

    @Override
    public GroupSetExclude groupSet(Set<String> groups) {
        return new DefaultGroupSetExclude(groups);
    }

    @Override
    public ModuleSetExclude moduleSet(Set<String> modules) {
        return new DefaultModuleSetExclude(modules);
    }
}
