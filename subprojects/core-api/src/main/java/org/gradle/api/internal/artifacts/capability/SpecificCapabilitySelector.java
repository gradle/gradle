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

package org.gradle.api.internal.artifacts.capability;

import org.gradle.api.artifacts.capability.CapabilitySelector;

/**
 * A {@link CapabilitySelector} that selects a specific capability by group and name.
 *
 * TODO: Make this public eventually. This was made private while we are
 *       still determining the best way to model these selectors.
 */
public interface SpecificCapabilitySelector extends CapabilitySelector {

    /**
     * The group of the capability to select.
     */
    String getGroup();

    /**
     * The name of the capability to select.
     */
    String getName();

}
