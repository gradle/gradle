/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.dependencygraph;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DependencyGraphNode {

    //TODO SF this type needs to go. It overlaps with ResolvedConfigurationIdentifier.

    String configuration;
    ModuleVersionIdentifier id;

    public DependencyGraphNode(String configuration, ModuleVersionIdentifier id) {
        this.configuration = configuration;
        this.id = id;
    }

    public String getConfiguration() {
        return configuration;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    @Override
    public String toString() {
        return id + "[" + configuration + "]";
    }
}
