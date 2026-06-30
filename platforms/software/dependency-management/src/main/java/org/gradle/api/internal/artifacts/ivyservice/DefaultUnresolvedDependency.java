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

import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentSelector;

// To be left around when `UnresolvedDependency` is removed. We directly reference
// this type using its `class` to access data separate from the public API type.
@SuppressWarnings("deprecation")
public class DefaultUnresolvedDependency implements UnresolvedDependency {

    private final ComponentSelector requested;
    private final org.gradle.api.artifacts.ModuleVersionSelector selector;
    private final Throwable problem;

    public DefaultUnresolvedDependency(
        ComponentSelector requested,
        org.gradle.api.artifacts.ModuleVersionSelector selector,
        Throwable problem
    ) {
        this.requested = requested;
        this.selector = selector;
        this.problem = problem;
    }

    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    @Deprecated
    public org.gradle.api.artifacts.ModuleVersionSelector getSelector() {
        return selector;
    }

    @Override
    public Throwable getProblem() {
        return problem;
    }

    @Override
    public String toString() {
        return selector.getGroup() + ":" + selector.getName() + ":" + selector.getVersion();
    }

}
