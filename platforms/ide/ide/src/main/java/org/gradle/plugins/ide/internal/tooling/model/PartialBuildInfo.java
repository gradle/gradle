/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

@NullMarked
public class PartialBuildInfo implements Serializable {

        private final String buildId;
        private final File projectDir;
        private final File scriptFile;

        public PartialBuildInfo(String buildId, File projectDir, @Nullable File scriptFile) {
            this.buildId = buildId;
            this.projectDir = projectDir;
            this.scriptFile = scriptFile;
        }

        public File getProjectDir() {
            return projectDir;
        }

        @Nullable
        public File getScriptFile() {
            return scriptFile;
        }

        public String getBuildId() {
            return buildId;
        }
    }
