/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Set;

/**
 * @author: Szczepan Faber, created at: 8/9/11
 */
public class LenientConfigurationImpl implements LenientConfiguration {
    private final AbstractResolvedConfiguration delegate;

    public LenientConfigurationImpl(AbstractResolvedConfiguration resolvedConfiguration) {
        this.delegate = resolvedConfiguration;
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<Dependency> dependencySpec) {
        return delegate.doGetFirstLevelModuleDependencies(dependencySpec);
    }

    public Set<File> getFiles(Spec<Dependency> dependencySpec) {
        return delegate.doGetFiles(dependencySpec);
    }

    public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
        return delegate.getUnresolvedDependencies();
    }
}
