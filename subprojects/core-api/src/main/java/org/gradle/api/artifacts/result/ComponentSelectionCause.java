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
package org.gradle.api.artifacts.result;

import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * The possible component selection causes. There are a limited number of causes, but each of them
 * can be provided with a custom description, via {@link ComponentSelectionDescriptor}.
 *
 * @since 4.6
 */
@UsedByScanPlugin
public enum ComponentSelectionCause {
    /**
     * This component was selected because it's the root component.
     * @since 4.6
     */
    ROOT("root"),

    /**
     * This component was selected because it was requested directly.
     * @since 4.6
     */
    REQUESTED("requested"),

    /**
     * This component was selected by a rule.
     * @since 4.6
     */
    SELECTED_BY_RULE("selected by rule"),

    /**
     * This component was selected because selection was forced on this version.
     * @since 4.6
     */
    FORCED("forced"),

    /**
     * This component was selected between several candidates during conflict resolution.
     * @since 4.6
     */
    CONFLICT_RESOLUTION("conflict resolution"),

    /**
     * This component was selected as a participant of a composite.
     * @since 4.6
     */
    COMPOSITE_BUILD("composite build substitution"),

    /**
     * This component was selected because another version was rejected by a rule
     * @since 4.6
     */
    REJECTION("rejection"),

    /**
     * This component was selected because of a dependency constraint
     * @since 4.6
     */
    CONSTRAINT("constraint"),

    /**
     * This component was selected because it was requested by a parent with a strict version.
     *
     * @since 6.0
     */
    BY_ANCESTOR("by ancestor");

    private final String defaultReason;

    ComponentSelectionCause(String defaultReason) {

        this.defaultReason = defaultReason;
    }

    /**
     * Returns the default reason.
     *
     * @since 4.6
     */
    public String getDefaultReason() {
        return defaultReason;
    }
}
