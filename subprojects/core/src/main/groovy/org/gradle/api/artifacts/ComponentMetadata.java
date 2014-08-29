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

package org.gradle.api.artifacts;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * Provides a read-only view of a resolved component's metadata, which typically originates from
 * a component descriptor (Ivy file, Maven POM).
 */
@Incubating
public interface ComponentMetadata {
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

}
