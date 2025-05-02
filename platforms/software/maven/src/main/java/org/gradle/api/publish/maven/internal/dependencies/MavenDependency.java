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
package org.gradle.api.publish.maven.internal.dependencies;

import org.gradle.api.artifacts.ExcludeRule;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Represents a dependency within the {@code <dependencies>} or {@code <dependencyManagement>}
 * blocks of a Maven POM. This is the "Maven view" of the dependency, after being converted
 * from Gradle's dependency model.
 */
public interface MavenDependency {
    /**
     * The group ID of this dependency.
     */
    String getGroupId();

    /**
     * The artifact ID of this dependency.
     */
    String getArtifactId();

    /**
     * The version of this dependency.
     */
    @Nullable
    String getVersion();

    /**
     * The type of this dependency.
     */
    @Nullable
    String getType();

    /**
     * The classifier of this dependency.
     */
    @Nullable
    String getClassifier();

    /**
     * The scope of this dependency.
     */
    @Nullable
    String getScope();

    /**
     * The exclude rules of this dependency.
     */
    Set<ExcludeRule> getExcludeRules();

    /**
     * If this dependency is marked optional.
     */
    boolean isOptional();
}
