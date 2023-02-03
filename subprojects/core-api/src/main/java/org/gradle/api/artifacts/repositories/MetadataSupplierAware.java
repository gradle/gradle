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

import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ComponentMetadataVersionLister;

/**
 * Interface for repositories which support custom metadata suppliers and/or version listers.
 * A custom version lister or metadata supplier can be used as an optimization technique to
 * avoid too many requests on a server. By providing such rules, a plugin or build author can
 * provide the necessary information to perform component selection without having to actually
 * fetch the component metadata on a server.
 *
 * @since 4.9
 */
public interface MetadataSupplierAware {
    /**
     * Sets a custom metadata rule, which is capable of supplying the metadata of a component (status, status scheme, changing flag)
     * whenever a dynamic version is requested. It can be used to provide metadata directly, instead of having to parse the Ivy
     * descriptor.
     *
     * @param rule the class of the rule. Gradle will instantiate a new rule for each dependency which requires metadata.
     *
     * @since 4.9
     */
    void setMetadataSupplier(Class<? extends ComponentMetadataSupplier> rule);

    /**
     * Sets a custom metadata rule, possibly configuring the rule.
     *
     * @param rule the class of the rule. Gradle will instantiate a new rule for each dependency which requires metadata.
     * @param configureAction the action to use to configure the rule.
     *
     * @since 4.9
     */
    void setMetadataSupplier(Class<? extends ComponentMetadataSupplier> rule, Action<? super ActionConfiguration> configureAction);

    /**
     * Sets a custom component versions lister. A versions lister will be called whenever a dynamic version is requested.
     *
     * @param lister the class of the lister.
     *
     * @since 4.9
     */
    void setComponentVersionsLister(Class<? extends ComponentMetadataVersionLister> lister);

    /**
     * Sets a custom component versions lister. A versions lister will be called whenever a dynamic version is requested.
     *
     * @param lister the class of the lister.
     * @param configureAction the action to use to configure the lister.
     *
     * @since 4.9
     */
    void setComponentVersionsLister(Class<? extends ComponentMetadataVersionLister> lister, Action<? super ActionConfiguration> configureAction);

}
