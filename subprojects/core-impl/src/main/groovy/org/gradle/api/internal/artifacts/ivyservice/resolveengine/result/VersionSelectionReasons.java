/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;

public class VersionSelectionReasons {
    public static final ModuleVersionSelectionReason REQUESTED = new DefaultModuleVersionSelectionReason(false, false, false, "requested");
    public static final ModuleVersionSelectionReason ROOT = new DefaultModuleVersionSelectionReason(false, false, false, "root");
    public static final ModuleVersionSelectionReason FORCED = new DefaultModuleVersionSelectionReason(true, false, false, "forced");
    public static final ModuleVersionSelectionReason CONFLICT_RESOLUTION = new DefaultModuleVersionSelectionReason(false, true, false, "conflict resolution");
    public static final ModuleVersionSelectionReason SELECTED_BY_RULE = new DefaultModuleVersionSelectionReason(false, false, true, "selected by rule");
    public static final ModuleVersionSelectionReason CONFLICT_RESOLUTION_BY_RULE = new DefaultModuleVersionSelectionReason(false, true, true, "selected by rule and conflict resolution");

    public static ModuleVersionSelectionReason withConflictResolution(ModuleVersionSelectionReason reason) {
        if (reason.isConflictResolution()) {
            return reason;
        } else if (reason == SELECTED_BY_RULE) {
            return CONFLICT_RESOLUTION_BY_RULE;
        } else if (reason == REQUESTED) {
            return CONFLICT_RESOLUTION;
        }
        throw new IllegalArgumentException("Cannot create conflict resolution selection reason for input: " + reason);
    }

    private static class DefaultModuleVersionSelectionReason implements ModuleVersionSelectionReason {

        private final boolean forced;
        private final boolean conflictResolution;
        private final boolean selectedByRule;
        private final String description;

        private DefaultModuleVersionSelectionReason(boolean forced, boolean conflictResolution, boolean selectedByRule, String description) {
            this.forced = forced;
            this.conflictResolution = conflictResolution;
            this.selectedByRule = selectedByRule;
            assert description != null;
            this.description = description;
        }

        public boolean isForced() {
            return forced;
        }

        public boolean isConflictResolution() {
            return conflictResolution;
        }

        public boolean isSelectedByRule() {
            return selectedByRule;
        }

        public String getDescription() {
            return description;
        }

        public String toString() {
            return description;
        }
    }
}