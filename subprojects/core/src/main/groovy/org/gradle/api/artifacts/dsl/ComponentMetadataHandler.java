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
package org.gradle.api.artifacts.dsl;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ComponentMetadataDetails;

/**
 * Allows the build to provide rules that modify the metadata of depended-on software components.
 *
 * <p>Possible uses of component metadata rules are:
 * <ul>
 *     <li>Setting the status and status scheme of a component, overriding the value specified in the component descriptor.</li>
 *     <li>Declaring whether or not a component is 'changing', thus impacting the cache behaviour of the component.</li>
 * </ul>
 *
 * <p> Example:
 * <pre autoTested=''>
 * dependencies {
 *     components {
 *         // Set the status and status scheme for every component belonging to a module in the group "org.foo"
 *         all { ComponentMetadataDetails details ->
 *             if (details.id.group == "org.foo") {
 *                 def version = details.id.version
 *                 // assuming status is last part of version string
 *                 details.status = version.substring(version.lastIndexOf("-") + 1)
 *                 details.statusScheme = ["bronze", "silver", "gold", "platinum"]
 *             }
 *         }
 *
 *         // Treat all components in the module "org.foo:bar" as changing
 *         withModule("org.foo:bar") { ComponentMetadataDetails details ->
 *             details.changing = true
 *         }
 *     }
 * }
 * </pre>
 *
 * @since 1.8
 */
@Incubating
public interface ComponentMetadataHandler {
    /**
     * Adds a rule action that may modify the metadata of any resolved software component.
     *
     * @param rule the rule to be added
     * @return this
     */
    ComponentMetadataHandler all(Action<? super ComponentMetadataDetails> rule);

    /**
     * Adds a rule closure that may modify the metadata of any resolved software component.
     *
     * <p>The supplied rule closure must declare a {@link ComponentMetadataDetails} as it's first parameter,
     * allowing the component metadata to be modified.
     *
     * <p>In addition, the rule can declare additional (read-only) parameters, which may provide extra details
     * about the component. The order of these additional parameters is not significant.
     *
     * <p>The following additional parameter types are supported:
     * <ul>
     *     <li>{@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor} - additional Ivy-specific
     *     metadata. Rules declaring this parameter will only be invoked for components packaged as an Ivy module.</li>
     * </ul>
     *
     * @param rule the rule to be added
     * @return this
     */
    ComponentMetadataHandler all(Closure<?> rule);

    /**
     * Adds a rule that may modify the metadata of any resolved software component.
     *
     * <p>The ruleSource is an Object that has a single rule method annotated with {@link org.gradle.model.Mutate}.
     *
     * <p>This rule method:
     * <ul>
     *     <li>must return void.</li>
     *     <li>must have {@link ComponentMetadataDetails} as the first parameter.</li>
     *     <li>may have an additional parameter of type {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor}.</li>
     * </ul>
     *
     * @param ruleSource  the rule source object to be added
     * @return this
     */
    ComponentMetadataHandler all(Object ruleSource);

    /**
     * Adds a rule that may modify the metadata of any resolved software component belonging to the specified module.
     *
     * @param id the module to apply this rule to in "group:module" format or as a {@link org.gradle.api.artifacts.ModuleIdentifier}
     * @param rule the rule to be added
     * @return this
     */
    ComponentMetadataHandler withModule(Object id, Action<? super ComponentMetadataDetails> rule);

    /**
     * Adds a rule that may modify the metadata of any resolved software component belonging to the specified module.
     *
     * <p>The rule closure parameter is subject to the same requirements as {@link #all(groovy.lang.Closure)}.
     *
     * @param id the module to apply this rule to in "group:module" format or as a {@link org.gradle.api.artifacts.ModuleIdentifier}
     * @param rule the rule to be added
     * @return this
     */
    ComponentMetadataHandler withModule(Object id, Closure<?> rule);

    /**
     * Adds a rule that may modify the metadata of any resolved software component belonging to the specified module.
     *
     * <p>The rule source parameter is subject to the same requirements as {@link #all(Object)}.
     *
     * @param id the module to apply this rule to in "group:module" format or as a {@link org.gradle.api.artifacts.ModuleIdentifier}
     * @param ruleSource the rule source object to be added
     * @return this
     */
    ComponentMetadataHandler withModule(Object id, Object ruleSource);

}
