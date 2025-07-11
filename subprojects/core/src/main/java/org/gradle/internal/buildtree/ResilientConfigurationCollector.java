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

package org.gradle.internal.buildtree;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ServiceScope(Scope.BuildTree.class)
public class ResilientConfigurationCollector {

    private final List<PartialBuildInfo> partialBuildInfos = new ArrayList<>();

    public void addVisitedBuilds(String buildId, File projectDir, File scriptFile) {
        partialBuildInfos.add(new PartialBuildInfo(buildId, projectDir, scriptFile));
    }

    public List<PartialBuildInfo> getPartialBuildInfos() {
        return ImmutableList.copyOf(partialBuildInfos);
    }

    public static class PartialBuildInfo implements Serializable {

        private final String buildId;
        private final File projectDir;
        private final File scriptFile;

        public PartialBuildInfo(String buildId, File projectDir, File scriptFile) {
            this.buildId = buildId;
            this.projectDir = projectDir;
            this.scriptFile = scriptFile;
        }

        public File getProjectDir() {
            return projectDir;
        }

        public File getScriptFile() {
            return scriptFile;
        }

        public String getBuildId() {
            return buildId;
        }
    }

    public static class ResilientConfigurationException extends RuntimeException implements Serializable {
        private final List<PartialBuildInfo> partialBuildInfos;

        public ResilientConfigurationException(List<PartialBuildInfo> partialBuildInfos, Exception e) {
            super("Configuration failed, but some builds were partially configured. See the details for more information. Original exception: " + e.getMessage(), e);
            this.partialBuildInfos = ImmutableList.copyOf(partialBuildInfos);
        }

        public List<PartialBuildInfo> getPartialBuildInfos() {
            return partialBuildInfos;
        }
    }
}
