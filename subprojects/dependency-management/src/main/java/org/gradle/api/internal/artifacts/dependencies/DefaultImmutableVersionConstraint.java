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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

// does not override equals() but hashCode() in order to cache the latter's
// pre-computed value to improve performance when used in HashMaps
@SuppressWarnings("checkstyle:EqualsHashCode")
public class DefaultImmutableVersionConstraint extends AbstractVersionConstraint implements ImmutableVersionConstraint, Serializable {
    private static final DefaultImmutableVersionConstraint EMPTY = new DefaultImmutableVersionConstraint("");
    private final String requiredVersion;
    private final String preferredVersion;
    private final String strictVersion;
    private final ImmutableList<String> rejectedVersions;
    @Nullable
    private final String requiredBranch;

    private final int hashCode;

    public DefaultImmutableVersionConstraint(String preferredVersion,
                                             String requiredVersion,
                                             String strictVersion,
                                             List<String> rejectedVersions,
                                             @Nullable String requiredBranch) {
        if (preferredVersion == null) {
            throw new IllegalArgumentException("Preferred version must not be null");
        }
        if (requiredVersion == null) {
            throw new IllegalArgumentException("Required version must not be null");
        }
        if (strictVersion == null) {
            throw new IllegalArgumentException("Strict version must not be null");
        }
        if (rejectedVersions == null) {
            throw new IllegalArgumentException("Rejected versions must not be null");
        }
        for (String rejectedVersion : rejectedVersions) {
            if (!GUtil.isTrue(rejectedVersion)) {
                throw new IllegalArgumentException("Rejected version must not be empty");
            }
        }
        this.preferredVersion = preferredVersion;
        this.requiredVersion = requiredVersion;
        this.strictVersion = strictVersion;
        this.rejectedVersions = ImmutableList.copyOf(rejectedVersions);
        this.requiredBranch = requiredBranch;
        this.hashCode = super.hashCode();
    }

    public DefaultImmutableVersionConstraint(String requiredVersion) {
        if (requiredVersion == null) {
            throw new IllegalArgumentException("Required version must not be null");
        }
        this.preferredVersion = "";
        this.requiredVersion = requiredVersion;
        this.strictVersion = "";
        this.rejectedVersions = ImmutableList.of();
        this.requiredBranch = null;
        this.hashCode = super.hashCode();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Nullable
    @Override
    public String getBranch() {
        return requiredBranch;
    }

    @Override
    public String getRequiredVersion() {
        return requiredVersion;
    }

    @Override
    public String getPreferredVersion() {
        return preferredVersion;
    }

    @Override
    public String getStrictVersion() {
        return strictVersion;
    }

    @Override
    public List<String> getRejectedVersions() {
        return rejectedVersions;
    }

    public static ImmutableVersionConstraint of(VersionConstraint versionConstraint) {
        if (versionConstraint instanceof ImmutableVersionConstraint) {
            return (ImmutableVersionConstraint) versionConstraint;
        }
        return new DefaultImmutableVersionConstraint(versionConstraint.getPreferredVersion(), versionConstraint.getRequiredVersion(), versionConstraint.getStrictVersion(), versionConstraint.getRejectedVersions(), versionConstraint.getBranch());
    }

    public static ImmutableVersionConstraint of(@Nullable String version) {
        if (version == null) {
            return of();
        }
        return new DefaultImmutableVersionConstraint(version);
    }

    public static ImmutableVersionConstraint of(String preferredVersion, String requiredVersion, String strictVersion, List<String> rejects) {
        return new DefaultImmutableVersionConstraint(preferredVersion, requiredVersion, strictVersion, rejects, null);
    }

    public static ImmutableVersionConstraint of() {
        return EMPTY;
    }
}
