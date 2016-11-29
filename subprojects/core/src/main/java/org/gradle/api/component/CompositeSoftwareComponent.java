/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.component;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;

/**
 * Represents a component that is composed from other components.
 */
@Incubating
public interface CompositeSoftwareComponent extends SoftwareComponent {
    /**
     * Returns the children of this component.
     */
    SoftwareComponentContainer getChildren();

    void children(Action<? super SoftwareComponentContainer> action);

    void composite(String name, Action<? super CompositeSoftwareComponent> configureAction);

    void child(String name, Action<? super ConsumableSoftwareComponent> configureAction);

    void fromConfiguration(String name, Configuration configuration, Action<? super ConsumableSoftwareComponent> configureAction);
}
