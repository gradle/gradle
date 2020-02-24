/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.Incubating;

/**
 * <p>Descriptor of a repository content, used to avoid reaching to
 * an external repository when not needed.</p>
 *
 * <p>Only inclusions can be defined at this level (cross-repository content).
 * Excludes need to be defined per-repository using {@link RepositoryContentDescriptor}.
 * Similarly to declaring includes on specific repositories via {@link ArtifactRepository#content(Action)},
 * inclusions are <i>extensive</i>, meaning that anything which doesn't match the includes will be
 * considered missing from the repository.
 * </p>
 *
 * @since 6.2
 */
@Incubating
public interface InclusiveRepositoryContentDescriptor {
    /**
     * Declares that an entire group should be searched for in this repository.
     *
     * @param group the group name
     */
    void includeGroup(String group);

    /**
     * Declares that an entire group should be searched for in this repository.
     *
     * @param groupRegex a regular expression of the group name
     */
    void includeGroupByRegex(String groupRegex);

    /**
     * Declares that an entire module should be searched for in this repository.
     *
     * @param group the group name
     * @param moduleName the module name
     */
    void includeModule(String group, String moduleName);

    /**
     * Declares that an entire module should be searched for in this repository, using regular expressions.
     *
     * @param groupRegex the group name regular expression
     * @param moduleNameRegex the module name regular expression
     */
    void includeModuleByRegex(String groupRegex, String moduleNameRegex);

    /**
     * Declares that a specific module version should be searched for in this repository.
     *
     * @param group the group name
     * @param moduleName the module name
     * @param version the module version
     */
    void includeVersion(String group, String moduleName, String version);

    /**
     * Declares that a specific module version should be searched for in this repository, using regular expressions.
     *
     * @param groupRegex the group name regular expression
     * @param moduleNameRegex the module name regular expression
     * @param versionRegex the module version regular expression
     */
    void includeVersionByRegex(String groupRegex, String moduleNameRegex, String versionRegex);
}
