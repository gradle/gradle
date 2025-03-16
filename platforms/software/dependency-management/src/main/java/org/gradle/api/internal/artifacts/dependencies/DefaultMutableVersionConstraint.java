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

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.VersionConstraintInternal;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Strings.nullToEmpty;

public class DefaultMutableVersionConstraint extends AbstractVersionConstraint implements VersionConstraintInternal {
    private String requiredVersion;
    private String preferredVersion;
    private String strictVersion;
    private String branch;
    private final List<String> rejectedVersions = new ArrayList<>(1);

    public DefaultMutableVersionConstraint(VersionConstraint versionConstraint) {
        this(versionConstraint.getPreferredVersion(), versionConstraint.getRequiredVersion(), versionConstraint.getStrictVersion(), versionConstraint.getRejectedVersions(), versionConstraint.getBranch());
    }

    public DefaultMutableVersionConstraint(String version) {
        this(null, version, null);
    }

    private DefaultMutableVersionConstraint(@Nullable String preferredVersion, String requiredVersion, @Nullable String strictVersion) {
        this(preferredVersion, requiredVersion, strictVersion, Collections.emptyList(), null);
    }

    private DefaultMutableVersionConstraint(@Nullable String preferredVersion, String requiredVersion, @Nullable String strictVersion, List<String> rejects, @Nullable String branch) {
        updateVersions(preferredVersion, requiredVersion, strictVersion);
        for (String reject : rejects) {
            this.rejectedVersions.add(nullToEmpty(reject));
        }
        this.branch = branch;
    }

    private void updateVersions(@Nullable String preferredVersion, @Nullable String requiredVersion, @Nullable String strictVersion) {
        this.preferredVersion = nullToEmpty(preferredVersion);
        this.requiredVersion = nullToEmpty(requiredVersion);
        this.strictVersion = nullToEmpty(strictVersion);
        this.rejectedVersions.clear();
    }

    public static DefaultMutableVersionConstraint withVersion(String version) {
        return new DefaultMutableVersionConstraint(version);
    }

    public static DefaultMutableVersionConstraint withStrictVersion(String version) {
        return new DefaultMutableVersionConstraint(null, version, version);
    }

    @Override
    public ImmutableVersionConstraint asImmutable() {
        return new DefaultImmutableVersionConstraint(preferredVersion, requiredVersion, strictVersion, rejectedVersions, branch);
    }

    @Nullable
    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public void setBranch(@Nullable String branch) {
        this.branch = branch;
    }

    @Override
    public String getRequiredVersion() {
        return requiredVersion;
    }

    @Override
    public void require(String version) {
        updateVersions(preferredVersion, version, null);
    }

    @Override
    public String getPreferredVersion() {
        return preferredVersion;
    }

    @Override
    public void prefer(String version) {
        updateVersions(version, requiredVersion, strictVersion);
    }

    @Override
    public String getStrictVersion() {
        return strictVersion;
    }

    @Override
    public void strictly(String version) {
        updateVersions(preferredVersion, version, version);
    }

    @Override
    public void reject(String... versions) {
        this.rejectedVersions.clear();
        Collections.addAll(rejectedVersions, versions);
    }

    @Override
    public void rejectAll() {
        updateVersions(null, null, null);
        this.rejectedVersions.add("+");
    }

    @Override
    public List<String> getRejectedVersions() {
       return rejectedVersions;
    }

    public String getVersion() {
        return requiredVersion.isEmpty() ? preferredVersion : requiredVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultMutableVersionConstraint that = (DefaultMutableVersionConstraint) o;

        if (requiredVersion != null ? !requiredVersion.equals(that.requiredVersion) : that.requiredVersion != null) {
            return false;
        }
        if (preferredVersion != null ? !preferredVersion.equals(that.preferredVersion) : that.preferredVersion != null) {
            return false;
        }
        if (strictVersion != null ? !strictVersion.equals(that.strictVersion) : that.strictVersion != null) {
            return false;
        }
        if (branch != null ? !branch.equals(that.branch) : that.branch != null) {
            return false;
        }
        return rejectedVersions.equals(that.rejectedVersions);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (requiredVersion != null ? requiredVersion.hashCode() : 0);
        result = 31 * result + (preferredVersion != null ? preferredVersion.hashCode() : 0);
        result = 31 * result + (strictVersion != null ? strictVersion.hashCode() : 0);
        result = 31 * result + (branch != null ? branch.hashCode() : 0);
        result = 31 * result + rejectedVersions.hashCode();
        return result;
    }
}
