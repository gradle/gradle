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

import java.util.Map;

public interface PomParent {
    /**
     * Gets POM, GAV and custom properties.
     *
     * @return Properties
     */
    Map<String, String> getProperties();

    /**
     * Gets declared dependencies.
     *
     * @return Dependencies
     */
    Map<MavenDependencyKey, PomDependencyData> getDependencies();

    /**
     * Gets declared dependency management.
     *
     * @return Dependency management
     */
    Map<MavenDependencyKey, PomDependencyMgt> getDependencyMgt();

    /**
     * Finds dependency management default coordinates for a dependency. Returns null if default cannot be found.
     *
     * @param dependencyKey Dependency key
     * @return Dependency management element or null
     */
    PomDependencyMgt findDependencyDefaults(MavenDependencyKey dependencyKey);
}
