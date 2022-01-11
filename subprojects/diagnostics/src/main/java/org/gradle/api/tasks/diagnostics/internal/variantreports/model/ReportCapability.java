/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.variantreports.model;

import org.gradle.api.Project;
import org.gradle.api.capabilities.Capability;

import java.util.Objects;

public final class ReportCapability {
    private final String group;
    private final String module;
    private final String version;
    private final boolean isDefault;

    private ReportCapability(String group, String module, String version, boolean isDefault) {
        this.group = group;
        this.module = module;
        this.version = version;
        this.isDefault = isDefault;
    }

    public static ReportCapability fromCapability(Capability capability) {
        return new ReportCapability(capability.getGroup(), capability.getName(), capability.getVersion(), false);
    }

    public static ReportCapability defaultCapability(Project project) {
        return new ReportCapability(Objects.toString(project.getGroup()), project.getName(), Objects.toString(project.getVersion()), true);
    }

    public String getGroup() {
        return group;
    }

    public String getModule() {
        return module;
    }

    public String getVersion() {
        return version;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
