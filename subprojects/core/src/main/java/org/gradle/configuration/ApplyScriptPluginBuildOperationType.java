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

package org.gradle.configuration;

import org.gradle.api.Nullable;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.io.File;

/**
 * Details about a script plugin being applied.
 *
 * @since 4.0
 */
public final class ApplyScriptPluginBuildOperationType implements BuildOperationType<ApplyScriptPluginBuildOperationType.Details, Void> {

    @UsedByScanPlugin
    public interface Details {

        /**
         * The absolute path to the file.
         */
        @Nullable
        String getFile();

        String getDisplayName();

    }

    public static class DetailsImpl implements Details {

        private File file;
        private String displayName;

        public DetailsImpl(@Nullable File file, String displayName) {
            this.file = file;
            this.displayName = displayName;
        }

        @Nullable
        public String getFile() {
            return file.getAbsolutePath();
        }

        public String getDisplayName() {
            return displayName;
        }

    }

    private ApplyScriptPluginBuildOperationType() {
    }
}
