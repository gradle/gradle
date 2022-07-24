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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories;

import org.gradle.api.artifacts.ModuleIdentifier;
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

public abstract class DelegatingExcludeFactory implements ExcludeFactory {
    protected final ExcludeFactory delegate;

    DelegatingExcludeFactory(ExcludeFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExcludeNothing nothing() {
        return delegate.nothing();
    }

    @Override
    public ExcludeEverything everything() {
        return delegate.everything();
    }

    @Override
    public GroupExclude group(String group) {
        return delegate.group(group);
    }

    @Override
    public ModuleExclude module(String module) {
        return delegate.module(module);
    }

    @Override
    public ModuleIdExclude moduleId(ModuleIdentifier id) {
        return delegate.moduleId(id);
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return delegate.anyOf(one, two);
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return delegate.allOf(one, two);
    }

    @Override
    public ExcludeSpec anyOf(Set<ExcludeSpec> specs) {
        return delegate.anyOf(specs);
    }

    @Override
    public ExcludeSpec allOf(Set<ExcludeSpec> specs) {
        return delegate.allOf(specs);
    }

    @Override
    public ExcludeSpec ivyPatternExclude(ModuleIdentifier moduleId, IvyArtifactName artifact, String matcher) {
        return delegate.ivyPatternExclude(moduleId, artifact, matcher);
    }

    @Override
    public ModuleIdSetExclude moduleIdSet(Set<ModuleIdentifier> modules) {
        return delegate.moduleIdSet(modules);
    }

    @Override
    public GroupSetExclude groupSet(Set<String> groups) {
        return delegate.groupSet(groups);
    }

    @Override
    public ModuleSetExclude moduleSet(Set<String> modules) {
        return delegate.moduleSet(modules);
    }
}
