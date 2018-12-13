/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.internal.artifacts.dependencies;

import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.artifacts.ResolvableDependency;

import javax.annotation.Nullable;
import java.util.Set;

public abstract class AbstractDependency implements ResolvableDependency, DependencyInternal {
    private String reason;
    private Set<String> requestedOptionalFeatures;
    private Set<String> requiredForOptionalFeatures;

    protected void copyTo(AbstractDependency target) {
        target.reason = reason;
    }

    public void resolve(DependencyResolveContext context) {
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public void because(String reason) {
        this.reason = reason;
    }

    @Nullable
    @Override
    public Set<String> getRequestedOptionalFeatures() {
        return requestedOptionalFeatures;
    }

    @Nullable
    @Override
    public Set<String> getRequiredForOptionalFeatures() {
        return requiredForOptionalFeatures;
    }

    @Override
    public void includeOptionalFeature(String featureId) {
        if (requestedOptionalFeatures == null) {
            requestedOptionalFeatures = Sets.newHashSetWithExpectedSize(1);
        }
        requestedOptionalFeatures.add(featureId);
    }

    @Override
    public void usedByOptionalFeature(String featureId) {
        if (requiredForOptionalFeatures == null) {
            requiredForOptionalFeatures = Sets.newHashSetWithExpectedSize(1);
        }
        requiredForOptionalFeatures.add(featureId);
    }
}
