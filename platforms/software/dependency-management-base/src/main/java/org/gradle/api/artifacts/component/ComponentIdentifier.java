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
package org.gradle.api.artifacts.component;

import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * An opaque immutable identifier for a component instance. There are various sub-interfaces that expose specific details about the identifier.
 *
 * @since 1.10
 */
@UsedByScanPlugin
public interface ComponentIdentifier {
    /**
     * Returns a human-consumable display name for this identifier.
     *
     * @return Component identifier display name
     * @since 1.10
     */
    String getDisplayName();
}
