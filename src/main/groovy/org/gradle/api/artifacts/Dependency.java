/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.artifacts;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@code Dependency} represents a dependency on the artifacts from a particular source.</p>
 *
 * @author Hans Dockter
 */
public interface Dependency {
    enum State { UNRESOLVED, RESOLVED, UNRESOLVABLE }

    String DEFAULT_CONFIGURATION = "default";
    String MASTER_CONFIGURATION = "master";
    String CLASSIFIER = "classifier";

    /**
     * Adds an exclude rule to exclude transitive dependencies of this dependency.
     *
     * @param excludeProperties the properties to define the exclude rule.
     * @return this
     */
    Dependency exclude(Map<String, String> excludeProperties);

    Set<ExcludeRule> getExcludeRules();

    String getGroup();

    String getName();

    String getVersion();

    boolean isTransitive();

    Dependency setTransitive(boolean transitive);

    Set<DependencyArtifact> getArtifacts();

    Dependency addArtifact(DependencyArtifact artifact);

    String getDependencyConfiguration();

    Dependency setDependencyConfiguration(String dependencyConfiguration);

    boolean contentEquals(Dependency dependency);

    Dependency copy();

//    State getState();
//
//    List<File> getFiles();
}
