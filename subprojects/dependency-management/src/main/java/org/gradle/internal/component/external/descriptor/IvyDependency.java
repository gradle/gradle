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

package org.gradle.internal.component.external.descriptor;

import org.gradle.api.artifacts.ModuleVersionSelector;

public class IvyDependency extends Dependency {
    private final String dynamicConstraintVersion;
    private final boolean force;
    private final boolean changing;
    private final boolean transitive;

    public IvyDependency(ModuleVersionSelector requested, String dynamicConstraintVersion, boolean force, boolean changing, boolean transitive) {
        super(requested);
        this.dynamicConstraintVersion = dynamicConstraintVersion;
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
    }

    public IvyDependency(ModuleVersionSelector requested) {
        this(requested, requested.getVersion(), false, false, true);
    }

    public boolean isForce() {
        return force;
    }

    public boolean isChanging() {
        return changing;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public String getDynamicConstraintVersion() {
        return dynamicConstraintVersion;
    }
}
