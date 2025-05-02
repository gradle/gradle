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

import org.gradle.api.Describable;
import org.gradle.api.Incubating;

/**
 * An opaque, immutable, selector for a capability of a variant.
 * <p>
 * A capability selector is attached to a {@link org.gradle.api.artifacts.component.ComponentSelector}
 * to specify the capability of the variant that should be selected from a given component.
 *
 * @implSpec Implementations must override equals and hash code
 *
 * @since 8.11
 */
@Incubating
public interface CapabilitySelector extends Describable {
}
