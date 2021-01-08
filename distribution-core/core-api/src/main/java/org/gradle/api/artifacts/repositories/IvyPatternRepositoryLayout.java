/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.artifacts.repositories;

import org.gradle.api.Action;

/**
 * A repository layout that uses user-supplied patterns. Each pattern will be appended to the base URI for the repository.
 * At least one artifact pattern must be specified. If no Ivy patterns are specified, then the artifact patterns will be used.
 * Optionally supports a Maven style layout for the 'organisation' part, replacing any dots with forward slashes.
 *
 * For examples see the reference for {@link org.gradle.api.artifacts.repositories.IvyArtifactRepository#patternLayout(Action)}.
 *
 * @since 2.3 (feature was already present in Groovy DSL, this type introduced in 2.3)
 */
public interface IvyPatternRepositoryLayout extends RepositoryLayout {

    /**
     * Adds an Ivy artifact pattern to define where artifacts are located in this repository.
     * @param pattern The ivy pattern
     */
    void artifact(String pattern);

    /**
     * Adds an Ivy pattern to define where ivy files are located in this repository.
     * @param pattern The ivy pattern
     */
    void ivy(String pattern);

    /**
     * Tells whether a Maven style layout is to be used for the 'organisation' part, replacing any dots with forward slashes.
     * Defaults to {@code false}.
     */
    boolean getM2Compatible();

    /**
     * Sets whether a Maven style layout is to be used for the 'organisation' part, replacing any dots with forward slashes.
     * Defaults to {@code false}.
     *
     * @param m2compatible whether a Maven style layout is to be used for the 'organisation' part
     */
    void setM2compatible(boolean m2compatible);
}
