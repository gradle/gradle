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
package org.gradle.api.artifacts.cache;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Represents a set of rules/actions that can be applied during dependency resolution.
 * Currently these are restricted to controlling caching, but these could possibly be extended in the future to include other manipulations.
 */
@Incubating
public interface ResolutionRules {
    /**
     * Apply a rule to control resolution of dependencies.
     * @param rule the rule to apply
     */
    void eachDependency(Action<? super DependencyResolutionControl> rule);

    /**
     * Apply a rule to control resolution of modules.
     * @param rule the rule to apply
     */
    void eachModule(Action<? super ModuleResolutionControl> rule);

    /**
     * Apply a rule to control resolution of artifacts.
     * @param rule the rule to apply
     */
    void eachArtifact(Action<? super ArtifactResolutionControl> rule);
}
