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
import org.gradle.api.artifacts.ComponentModuleMetadataDetails;

/**
 * Allows to modify the metadata of depended-on software components.
 *
 * <p> Example:
 * <pre autoTested=''>
 * dependencies {
 *     components {
 *         //triggered during dependency resolution, for every module:
 *         all { ComponentMetadataDetails details ->
 *             if (details.id.group == "org.foo") {
 *                 def version = details.id.version
 *                 // assuming status is last part of version string
 *                 details.status = version.substring(version.lastIndexOf("-") + 1)
 *                 details.statusScheme = ["bronze", "silver", "gold", "platinum"]
 *             }
 *         }
 *
 *         // triggered during dependency resolution for only the "org.foo:bar" module:
 *         withModule("org.foo:bar") { ComponentMetadataDetails details ->
 *             details.changing = true
 *         }
 *
 *         //Configuring component module metadata for the entire "google-collections" module,
 *         // declaring that legacy library was replaced with "guava".
 *         //This way, Gradle's conflict resolution can use this information and use "guava"
 *         // in case both libraries appear in the same dependency tree.
 *         module("com.google.collections:google-collections").replacedBy("com.google.guava:guava")
 *     }
 * }
 * </pre>
 *
 * @since 1.8
 */
@Incubating
public interface ComponentMetadataHandler {
    /**
     * Adds a rule to modify the metadata of depended-on software components.
     * For example, this allows to set a component's status and status scheme
     * from within the build script, overriding any value specified in the
     * component descriptor.
     *
     * @param rule the rule to be added
     * @return this
     */
    ComponentMetadataHandler all(Action<? super ComponentMetadataDetails> rule);

    /**
     * Adds a rule to modify the metadata of depended-on software components.
     * For example, this allows setting a component's status and status scheme
     * from within the build script, overriding any value specified in the
     * component descriptor.
     *
     * <p>The rule must declare a {@link ComponentMetadataDetails} as it's first parameter,
     * allowing the component metadata to be modified.
     *
     * <p>In addition, the rule can declare additional (read-only) parameters, which may provide extra details
     * about the component. The order of these additional parameters is irrelevant.
     *
     * <p>Presently, the following additional parameter types are supported:
     * <ul>
     *     <li>{@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor} Additional Ivy-specific
     *     metadata. Rules declaring this parameter will only be invoked for components packaged as an Ivy module.</li>
     * </ul>
     *
     * @param rule the rule to be added
     * @return this
     */
    ComponentMetadataHandler all(Closure<?> rule);

    /**
     * Adds a rule source object to modify the metadata of depended-on software components.
     * For example, this allows setting a component's status and status scheme
     * from within the build script, overriding any value specified in the
     * component descriptor.
     *
     * The ruleSource provides the rule as exactly one rule method annotated with {@link org.gradle.model.Mutate}.
     *
     * This rule method:
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
     * Adds a rule to modify the metadata of the specified module.
     * For example, this allows to set a component's status and status scheme
     * from within the build script, overriding any value specified in the
     * component descriptor.
     *
     * @param rule the rule to be added
     * @return this
     */
    ComponentMetadataHandler withModule(Object id, Action<? super ComponentMetadataDetails> rule);

    /**
     * Adds a rule to modify the metadata of the specified module.
     * For example, this allows setting a component's status and status scheme
     * from within the build script, overriding any value specified in the
     * component descriptor.
     *
     * <p>The rule must declare a {@link ComponentMetadataDetails} as it's first parameter,
     * allowing the component metadata to be modified.
     *
     * <p>In addition, the rule can declare additional (read-only) parameters, which may provide extra details
     * about the component. The order of these additional parameters is irrelevant.
     *
     * <p>Presently, the following additional parameter types are supported:
     * <ul>
     *     <li>{@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor} Additional Ivy-specific
     *     metadata. Rules declaring this parameter will only be invoked for components packaged as an Ivy module.</li>
     * </ul>
     *
     * @param rule the rule to be added
     * @return this
     */
    ComponentMetadataHandler withModule(Object id, Closure<?> rule);

    /**
     * Adds a rule source object to modify the metadata of the specified module.
     * For example, this allows setting a component's status and status scheme
     * from within the build script, overriding any value specified in the
     * component descriptor.
     *
     * The ruleSource provides the rule as exactly one rule method annotated with {@link org.gradle.model.Mutate}.
     *
     * This rule method:
     * <ul>
     *     <li>must return void.</li>
     *     <li>must have {@link ComponentMetadataDetails} as the first parameter.</li>
     *     <li>may have an additional parameter of type {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor}.</li>
     * </ul>
     *
     * @param ruleSource  the rule source object to be added
     * @return this
     */
    ComponentMetadataHandler withModule(Object id, Object ruleSource);

    /**
     * Enables configuring component module metadata.
     * This metadata applies to the entire component module (e.g. "group:name", like "org.gradle:gradle-core") regardless of the component version.
     *
     * <pre autoTested=''>
     * //declaring that google collections are replaced by guava
     * //so that conflict resolution can take advantage of this information:
     * dependencies.components.module('com.google.collections:google-collections').replacedBy('com.google.guava:guava')
     * </pre>
     *
     * @param moduleNotation an identifier of the module. String "group:name", e.g. 'org.gradle:gradle-core'
     * or an instance of {@link org.gradle.api.artifacts.ModuleIdentifier}
     *
     * @return an object that allows querying and configuring metadata of given component module
     * @since 2.2
     */
    public ComponentModuleMetadataDetails module(Object moduleNotation);
}
