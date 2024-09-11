/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.artifacts.capability;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * A {@link CapabilitySelector} that selects a specific capability by group and name.
 *
 * @since 8.11
 */
@Incubating
@HasInternalProtocol
public interface SpecificCapabilitySelector extends CapabilitySelector {

    /**
     * The group of the capability to select.
     *
     * @since 8.11
     */
    String getGroup();

    /**
     * The name of the capability to select.
     *
     * @since 8.11
     */
    String getName();

}
