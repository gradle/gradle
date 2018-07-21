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

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import org.gradle.api.artifacts.VersionConstraint;

public abstract class AbstractVersionConstraint implements VersionConstraint {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }

        AbstractVersionConstraint that = (AbstractVersionConstraint) o;

        return Objects.equal(getPreferredVersion(), that.getPreferredVersion())
            && Objects.equal(getStrictVersion(), that.getStrictVersion())
            && Objects.equal(getBranch(), that.getBranch())
            && Objects.equal(getRejectedVersions(), that.getRejectedVersions());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getPreferredVersion(), getStrictVersion(), getRejectedVersions());
    }

    @Override
    public String toString() {
        return getPreferredVersion() + (getRejectedVersions().isEmpty() ? "" : " {rejects: " + Joiner.on(" & ").join(getRejectedVersions()) + "}") + (getBranch() == null ? "" : " {branch: " + getBranch() + "}");
    }
}
