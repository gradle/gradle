/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.attributes.Attribute;

/**
 * <p>Descriptor of a repository content, used to avoid reaching to
 * an external repository when not needed.</p>
 *
 * <p>Excludes are applied after includes. This means that by default, everything is included and nothing excluded.
 * If includes are added, then if the module doesn't match any of the includes, it's excluded. Then if it does, but
 * it also matches one of the excludes, it's also excluded.</p>
 *
 * @since 5.1
 */
public interface RepositoryContentDescriptor extends InclusiveRepositoryContentDescriptor {

    /**
     * Declares that an entire group should be searched for in this repository.
     *
     * @param group the group name
     */
    @Override
    void includeGroup(String group);

    /**
     * Declares that an entire group should be searched for in this repository.
     *
     * @param groupRegex a regular expression of the group name
     */
    @Override
    void includeGroupByRegex(String groupRegex);

    /**
     * Declares that an entire module should be searched for in this repository.
     *
     * @param group the group name
     * @param moduleName the module name
     */
    @Override
    void includeModule(String group, String moduleName);

    /**
     * Declares that an entire module should be searched for in this repository, using regular expressions.
     *
     * @param groupRegex the group name regular expression
     * @param moduleNameRegex the module name regular expression
     */
    @Override
    void includeModuleByRegex(String groupRegex, String moduleNameRegex);

    /**
     * Declares that a specific module version should be searched for in this repository.
     * <p>
     * The version notation supports range notations like {@code [1.0,2.0[}.
     *
     * @param group the group name
     * @param moduleName the module name
     * @param version the module version
     */
    @Override
    void includeVersion(String group, String moduleName, String version);

    /**
     * Declares that a specific module version should be searched for in this repository, using regular expressions.
     * <p>
     * The version notation for a regex will be matched against a single version and does not support range notations.
     *
     * @param groupRegex the group name regular expression
     * @param moduleNameRegex the module name regular expression
     * @param versionRegex the module version regular expression
     */
    @Override
    void includeVersionByRegex(String groupRegex, String moduleNameRegex, String versionRegex);

    /**
     * Declares that an entire group shouldn't be searched for in this repository.
     *
     * @param group the group name
     */
    void excludeGroup(String group);

    /**
     * Declares that an entire group shouldn't be searched for in this repository.
     *
     * @param groupRegex the group name regular expression
     */
    void excludeGroupByRegex(String groupRegex);

    /**
     * Declares that an entire module shouldn't be searched for in this repository.
     *
     * @param group the group name
     * @param moduleName the module name
     */
    void excludeModule(String group, String moduleName);

    /**
     * Declares that an entire module shouldn't be searched for in this repository, using regular expressions.
     *
     * @param groupRegex the group name regular expression
     * @param moduleNameRegex the module name regular expression
     */
    void excludeModuleByRegex(String groupRegex, String moduleNameRegex);

    /**
     * Declares that a specific module version shouldn't be searched for in this repository.
     * <p>
     * The version notation for a regex will be matched against a single version and does not support range notations.
     *
     * @param group the group name
     * @param moduleName the module name
     * @param version the module version
     */
    void excludeVersion(String group, String moduleName, String version);

    /**
     * Declares that a specific module version shouldn't be searched for in this repository, using regular expressions.
     * <p>
     * The version notation for a regex will be matched against a single version and does not support range notations.
     *
     * @param groupRegex the group name
     * @param moduleNameRegex the module name
     * @param versionRegex the module version
     */
    void excludeVersionByRegex(String groupRegex, String moduleNameRegex, String versionRegex);

    /**
     * Declares that this repository should only be used for a specific
     * set of configurations. Defaults to any configuration
     *
     * @param configurationNames the names of the configurations the repository will be used for
     */
    void onlyForConfigurations(String... configurationNames);

    /**
     * Declares that this repository should not be used for a specific
     * set of configurations. Defaults to any configuration
     *
     * @param configurationNames the names of the configurations the repository will not be used for
     */
    void notForConfigurations(String... configurationNames);

    /**
     * Declares that this repository will only be searched if the consumer requires a
     * specific attribute.
     * @param attribute the attribute
     * @param validValues the list of accepted values
     * @param <T> the type of the attribute
     */
    <T> void onlyForAttribute(Attribute<T> attribute, T... validValues);
}
