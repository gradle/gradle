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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.Optimizations;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.List;
import java.util.Set;

public class DefaultExcludeFactory implements ExcludeFactory {
    @Override
    public ExcludeSpec nothing() {
        return DefaultExcludeNothing.get();
    }

    @Override
    public ExcludeSpec everything() {
        return DefaultExcludeEverything.get();
    }

    @Override
    public ExcludeSpec group(String group) {
        return DefaultGroupExclude.of(group);
    }

    @Override
    public ExcludeSpec module(String module) {
        return DefaultModuleExclude.of(module);
    }

    @Override
    public ExcludeSpec moduleId(ModuleIdentifier id) {
        return DefaultModuleIdExclude.of(id);
    }

    @Override
    public ExcludeSpec artifact(ModuleIdentifier id, IvyArtifactName artifact) {
        return DefaultModuleArtifactExclude.of(id, artifact);
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return Optimizations.optimizeAnyOf(one, two, (a, b) -> DefaultExcludeAnyOf.of(ImmutableList.of(a, b)));
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return Optimizations.optimizeAllOf(this, one, two, (a, b) -> DefaultExcludeAllOf.of(ImmutableList.of(a, b)));
    }

    @Override
    public ExcludeSpec anyOf(List<ExcludeSpec> specs) {
        return DefaultExcludeAnyOf.of(specs);
    }

    @Override
    public ExcludeSpec allOf(List<ExcludeSpec> specs) {
        return DefaultExcludeAllOf.of(specs);
    }

    @Override
    public ExcludeSpec ivyPatternExclude(ModuleIdentifier moduleId, IvyArtifactName artifact, String matcher) {
        return DefaultIvyPatternMatcherExcludeRuleSpec.of(moduleId, artifact, matcher);
    }

    @Override
    public ExcludeSpec moduleSet(Set<ModuleIdentifier> modules) {
        return DefaultModuleSetExclude.of(modules);
    }
}
