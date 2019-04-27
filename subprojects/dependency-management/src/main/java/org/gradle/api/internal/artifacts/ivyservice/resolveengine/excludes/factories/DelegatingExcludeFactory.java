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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.List;
import java.util.Set;

public abstract class DelegatingExcludeFactory implements ExcludeFactory {
    protected final ExcludeFactory delegate;

    public DelegatingExcludeFactory(ExcludeFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExcludeSpec nothing() {
        return delegate.nothing();
    }

    @Override
    public ExcludeSpec everything() {
        return delegate.everything();
    }

    @Override
    public ExcludeSpec group(String group) {
        return delegate.group(group);
    }

    @Override
    public ExcludeSpec module(String module) {
        return delegate.module(module);
    }

    @Override
    public ExcludeSpec moduleId(ModuleIdentifier id) {
        return delegate.moduleId(id);
    }

    @Override
    public ExcludeSpec artifact(ModuleIdentifier id, IvyArtifactName artifact) {
        return delegate.artifact(id, artifact);
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
    public ExcludeSpec anyOf(List<ExcludeSpec> specs) {
        return delegate.anyOf(specs);
    }

    @Override
    public ExcludeSpec allOf(List<ExcludeSpec> specs) {
        return delegate.allOf(specs);
    }

    @Override
    public ExcludeSpec ivyPatternExclude(ModuleIdentifier moduleId, IvyArtifactName artifact, String matcher) {
        return delegate.ivyPatternExclude(moduleId, artifact, matcher);
    }

    @Override
    public ExcludeSpec moduleSet(Set<ModuleIdentifier> modules) {
        return delegate.moduleSet(modules);
    }
}
