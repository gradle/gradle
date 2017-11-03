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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;

public class DefaultImmutableVersionConstraint implements ImmutableVersionConstraint {
    private final String preferredVersion;
    private final VersionSelector preferredSelector;
    private final VersionSelector rejectedSelector;

    public DefaultImmutableVersionConstraint(String preferredVersion,
                                      VersionSelector preferredSelector,
                                      VersionSelector rejectedSelector) {
        this.preferredVersion = preferredVersion;
        this.preferredSelector = preferredSelector;
        this.rejectedSelector = rejectedSelector;
    }

    @Override
    public VersionSelector getPreferredSelector() {
        return preferredSelector;
    }

    @Override
    public VersionSelector getRejectedSelector() {
        return rejectedSelector;
    }

    @Override
    public String getPreferredVersion() {
        return preferredVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultImmutableVersionConstraint that = (DefaultImmutableVersionConstraint) o;

        if (!preferredSelector.equals(that.preferredSelector)) {
            return false;
        }
        return rejectedSelector != null ? rejectedSelector.equals(that.rejectedSelector) : that.rejectedSelector == null;
    }

    @Override
    public int hashCode() {
        int result = preferredSelector.hashCode();
        result = 31 * result + (rejectedSelector != null ? rejectedSelector.hashCode() : 0);
        return result;
    }
}
