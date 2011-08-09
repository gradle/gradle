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

import org.gradle.api.artifacts.*;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * Lenient configuration when everything is neatly resolved. Most methods just delegate.
 *
 * @author: Szczepan Faber, created at: 8/9/11
 */
public class DelegatingLenientConfiguration implements LenientConfiguration {
    private final ResolvedConfiguration delegate;

    public DelegatingLenientConfiguration(ResolvedConfiguration delegate) {
        assert !delegate.hasError();
        this.delegate = delegate;
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<Dependency> dependencySpec) {
        return delegate.getFirstLevelModuleDependencies(dependencySpec);
    }

    public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
        //no errors so everything is resolved, hence empty unresolved list.
        return Collections.emptySet();
    }

    public Set<File> getFiles(Spec<Dependency> dependencySpec) {
        return delegate.getFiles(dependencySpec);
    }
}
