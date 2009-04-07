/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.artifacts.maven;

import java.util.Map;

/**
 * Defines a set of rules for how to map the Gradle dependendencies to a pom. This mapping is based
 * on the configuration the dependencies belong to.
 *
 * @author Hans Dockter
 */
public interface Conf2ScopeMappingContainer {
    String PROVIDED = "provided";
    String COMPILE = "compile";
    String RUNTIME = "runtime";
    String TEST = "test";

    /**
     * <p>Specifies that dependencies of a certain configuration should be mapped against a certain
     * Maven scope. A configuration can be mapped to one and only one scope. If this method is called
     * more than once for a particular configuration, the last call wins.</p>
     *
     * See {@link #getScope(String[])} for the rules how a scope is choosen from a set of mappings.
     *
     * @param priority a number that is used for comparison with the priority of other scopes.
     * @param configuration a Gradle configuration name (must not be null).
     * @param scope A Maven scope name (must not be null)
     * @return this
     * @see #getScope(String[]) 
     */
    Conf2ScopeMappingContainer addMapping(int priority, String configuration, String scope);

    /**
     * Returns a scope that corresponds to the given configurations. Dependencies can belong to more than one
     * configuration. But it can only belong to one Maven scope, due to the nature of a Maven pom.
     *
     * <p>Which scope is returned depends on the existing mappings. See {@link #addMapping(int, String, String)}. If
     * only one configuration is mapped, this mapping is used to choose the scope. If more than one configuraton of a
     * dependency is mapped, and those mappings all map to the same scope, this scope is used. If more than one
     * configuration is mapped and the mappings map to different scopes, the mapping with the highest priority is used.
     * If there is more than one mapping with the highest priority and those mappings map to different scopes, an
     * exception is thrown.</p>
     *
     * @param configurations The configuration names 
     * @return The scope corresponding to the given configurations. Returns null if no such scope can be found.
     * @see #addMapping(int, String, String) 
     */
    String getScope(String... configurations);

    /**
     * Returns the mapping for a particular configuration.
     * @param configuration The configuration name (must not be null)
     * @return The mapping belonging to the configuration or null, if no such mapping exists.
     */
    Conf2ScopeMapping getMapping(String configuration);

    /**
     * Returns a map with all the configuration to scope mappings. If no such mapping has been defined,
     * an empty map is returned.
     *
     * @see #addMapping(int, String, String) 
     */
    Map<String, Conf2ScopeMapping> getMappings();

    /**
     * Returns whether unmapped configuration should be skipped or not. Defaults to true.
     * @see #setSkipUnmappedConfs(boolean) 
     */
    boolean isSkipUnmappedConfs();

    /**
     * Sets, whether unmapped configuration should be skipped or not. If this is set to
     * false, dependencies belonging to unmapped configurations will be added to the Maven pom with no
     * scope specified. This means they belong to the Maven default scope, which is 'compile'.
     */
    void setSkipUnmappedConfs(boolean skipDependenciesWithUnmappedConfiguration);
}
