/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provider.provenance;

import org.gradle.api.internal.provider.PropertyHost;

/** Enabled project-only attribution services. Ambient attribution is diagnostic information, never authority. */
public interface PropertyProvenanceHost extends PropertyHost {
    /** The owning project, independent of the currently configuring source. */
    ScopeIdentity getOwnerScope();

    /** Allocates an occurrence namespace for a new tracked property, without retaining the property. */
    String newOccurrenceScope();

    /** Returns shared descriptors for the current application, or honest unknown attribution. */
    Attribution currentAttribution();
}
