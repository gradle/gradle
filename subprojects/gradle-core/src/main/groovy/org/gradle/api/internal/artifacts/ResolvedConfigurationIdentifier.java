/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.apache.ivy.core.module.id.ModuleRevisionId;

public class ResolvedConfigurationIdentifier {
    private final String moduleGroup;
    private final String moduleName;
    private final String moduleVersion;
    private final String configuration;

    public ResolvedConfigurationIdentifier(String moduleGroup, String moduleName, String moduleVersion,
                                           String configuration) {
        this.moduleGroup = moduleGroup;
        this.moduleName = moduleName;
        this.moduleVersion = moduleVersion;
        this.configuration = configuration;
    }

    public ResolvedConfigurationIdentifier(ModuleRevisionId moduleRevisionId, String configuration) {
        this(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision(),
                configuration);
    }

    public String getConfiguration() {
        return configuration;
    }

    public String getModuleGroup() {
        return moduleGroup;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s:%s", moduleGroup, moduleName, moduleVersion, configuration);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ResolvedConfigurationIdentifier that = (ResolvedConfigurationIdentifier) o;

        if (!moduleGroup.equals(that.moduleGroup)) {
            return false;
        }
        if (!moduleName.equals(that.moduleName)) {
            return false;
        }
        if (!moduleVersion.equals(that.moduleVersion)) {
            return false;
        }
        if (!configuration.equals(that.configuration)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return moduleGroup.hashCode() ^ moduleName.hashCode() ^ configuration.hashCode();
    }
}
