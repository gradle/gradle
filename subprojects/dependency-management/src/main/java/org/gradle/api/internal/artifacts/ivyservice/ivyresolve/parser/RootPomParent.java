/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomReader.PomDependencyData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt;

import java.util.Collections;
import java.util.Map;

public class RootPomParent implements PomParent {
    private final Map<String, String> properties = Collections.emptyMap();
    private final Map<MavenDependencyKey, PomReader.PomDependencyData> dependencies = Collections.emptyMap();
    private final Map<MavenDependencyKey, PomDependencyMgt> dependencyMgts = Collections.emptyMap();

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public Map<MavenDependencyKey, PomDependencyData> getDependencies() {
        return dependencies;
    }

    @Override
    public Map<MavenDependencyKey, PomDependencyMgt> getDependencyMgt() {
        return dependencyMgts;
    }

    @Override
    public PomDependencyMgt findDependencyDefaults(MavenDependencyKey dependencyKey) {
        return null;
    }
}
