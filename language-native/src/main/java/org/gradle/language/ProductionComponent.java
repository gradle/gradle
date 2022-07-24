/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language;

import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.provider.Provider;

/**
 * Represents a component that is the main product of a project.
 *
 * @since 4.5
 */
public interface ProductionComponent extends SoftwareComponent {
    /**
     * Returns the binary of the component to use as the default for development.
     */
    Provider<? extends SoftwareComponent> getDevelopmentBinary();
}
