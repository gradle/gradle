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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;

import java.util.List;

/**
 * Describes a resolved component's metadata, which typically originates from
 * a component descriptor (Ivy file, Maven POM). Some parts of the metadata can be changed
 * via metadata rules (see {@link org.gradle.api.artifacts.dsl.ComponentMetadataHandler}.
 *
 * @since 1.8
 */
@Incubating
@NonExtensible
public interface ComponentMetadataDetails {
    /**
     * Returns the identifier of the component.
     *
     * @return the identifier of the component.
     */
    ModuleVersionIdentifier getId();

    /**
     * Tells whether the component is changing or immutable.
     *
     * @return whether the component is changing or immutable.
     */
    boolean isChanging();

    /**
     * Returns the status of the component. Must
     * match one of the values in {@link #getStatusScheme()}.
     *
     * @return the status of the component
     */
    String getStatus();

    /**
     * Returns the status scheme of the component. Values are
     * ordered from least to most mature status.
     * Defaults to {@code ["integration", "milestone", "release"]}.
     *
     * @return the status scheme of the component
     */
    List<String> getStatusScheme();

    /**
     * Sets whether the component is changing or immutable.
     *
     * @param changing whether the component is changing or immutable
     */
    void setChanging(boolean changing);

    /**
     * Sets the status of the component. Must
     * match one of the values in {@link #getStatusScheme()}.
     *
     * @param status the status of the component
     */
    void setStatus(String status);

    /**
     * Sets the status scheme of the component. Values are ordered
     * from least to most mature status.
     *
     * @param statusScheme the status scheme of the component
     */
    void setStatusScheme(List<String> statusScheme);
}
