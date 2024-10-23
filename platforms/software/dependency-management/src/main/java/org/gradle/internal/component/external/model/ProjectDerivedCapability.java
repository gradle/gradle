/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.component.external.model;

import com.google.common.base.Objects;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;

public class ProjectDerivedCapability implements CapabilityInternal {
    private final Project project;
    private final String featureName;

    private volatile String capabilityName;

    public ProjectDerivedCapability(Project project) {
        this(project, null);
    }

    public ProjectDerivedCapability(Project project, @Nullable String featureName) {
        this.project = project;
        this.featureName = featureName;
    }

    @Override
    public String getGroup() {
        return notNull("group", project.getGroup());
    }

    @Override
    public String getName() {
        if (capabilityName == null) {
            capabilityName = computeCapabilityName(project, featureName);
        }
        return capabilityName;
    }

    private static String computeCapabilityName(Project project, @Nullable String featureName) {
        if (featureName == null) {
            return project.getName();
        }
        return project.getName() + "-" + TextUtil.camelToKebabCase(featureName);
    }

    @Override
    public String getVersion() {
        return notNull("version", project.getVersion());
    }

    @Override
    public int hashCode() {
        // See DefaultImmutableCapability#computeHashcode
        int hash = getVersion().hashCode();
        hash = 31 * hash + getName().hashCode();
        hash = 31 * hash + getGroup().hashCode();
        return  hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Capability)) {
            return false;
        }

        Capability that = (Capability) o;
        return Objects.equal(getGroup(), that.getGroup())
            && Objects.equal(getName(), that.getName())
            && Objects.equal(getVersion(), that.getVersion());

    }

    private static String notNull(String id, Object o) {
        if (o == null) {
            throw new InvalidUserDataException(id + " must not be null");
        }
        return o.toString();
    }

    @Override
    public String getCapabilityId() {
        return getGroup() + ":" + getName();
    }
}
