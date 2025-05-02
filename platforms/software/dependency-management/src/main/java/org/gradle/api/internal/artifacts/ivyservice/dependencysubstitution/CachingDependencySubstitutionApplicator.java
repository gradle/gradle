/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.component.model.DependencyMetadata;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.HashMap;
import java.util.Map;

/**
 * This caching applicator is not intended to be called from places where it can be executed concurrently.
 */
@NotThreadSafe
public class CachingDependencySubstitutionApplicator implements DependencySubstitutionApplicator {
    private final DependencySubstitutionApplicator delegate;
    private final Map<ComponentSelector, SubstitutionResult> cache = new HashMap<>();

    public CachingDependencySubstitutionApplicator(DependencySubstitutionApplicator delegate) {
        this.delegate = delegate;
    }


    @Override
    public SubstitutionResult apply(DependencyMetadata dependency) {
        ComponentSelector selector = dependency.getSelector();
        return cache.computeIfAbsent(selector, s -> delegate.apply(dependency));
    }
}
