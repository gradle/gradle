/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultResolvedProjectConfigurationResult implements ResolvedProjectConfigurationResult {
    private final ProjectComponentIdentifier id;
    private final Set<String> targetConfigurations = new LinkedHashSet<String>();

    public DefaultResolvedProjectConfigurationResult(ProjectComponentIdentifier id) {
        this.id = id;
    }

    @Override
    public ProjectComponentIdentifier getId() {
        return id;
    }

    @Override
    public Set<String> getTargetConfigurations() {
        return targetConfigurations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultResolvedProjectConfigurationResult that = (DefaultResolvedProjectConfigurationResult) o;

        if (!id.equals(that.id)) {
            return false;
        }
        if (!targetConfigurations.equals(that.targetConfigurations)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + targetConfigurations.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return id + ":" + targetConfigurations;
    }
}
