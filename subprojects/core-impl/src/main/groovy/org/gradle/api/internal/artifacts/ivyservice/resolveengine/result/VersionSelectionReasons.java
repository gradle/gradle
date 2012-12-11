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

/**
 * by Szczepan Faber, created at: 10/1/12
 */
public class VersionSelectionReasons {
    public static final ModuleVersionSelectionReason REQUESTED = new DefaultModuleVersionSelectionReason(false, false, "requested");
    public static final ModuleVersionSelectionReason ROOT = new DefaultModuleVersionSelectionReason(false, false, "root");
    public static final ModuleVersionSelectionReason FORCED = new DefaultModuleVersionSelectionReason(true, false, "forced");
    public static final ModuleVersionSelectionReason CONFLICT_RESOLUTION = new DefaultModuleVersionSelectionReason(false, true, "conflict resolution");

    private static class DefaultModuleVersionSelectionReason implements ModuleVersionSelectionReason {

        private final boolean forced;
        private final boolean conflictResolution;
        private final String description;

        private DefaultModuleVersionSelectionReason(boolean forced, boolean conflictResolution, String description) {
            this.forced = forced;
            this.conflictResolution = conflictResolution;
            assert description != null;
            this.description = description;
        }

        public boolean isForced() {
            return forced;
        }

        public boolean isConflictResolution() {
            return conflictResolution;
        }

        public String getDescription() {
            return description;
        }

        //TODO At some point we want to provide information if version was requested in the graph.
        //Perhaps a method like isRequested(). Not requested means that some particular version was forced but no dependency have requested this version.
    }
}