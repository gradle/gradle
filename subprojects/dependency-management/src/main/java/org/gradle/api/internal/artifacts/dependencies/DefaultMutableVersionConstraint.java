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

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.VersionConstraintInternal;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Strings.nullToEmpty;

public class DefaultMutableVersionConstraint extends AbstractVersionConstraint implements VersionConstraintInternal {
    private String preferredVersion;
    private String strictVersion;
    private String branch;
    private final List<String> rejectedVersions = Lists.newArrayListWithExpectedSize(1);

    public DefaultMutableVersionConstraint(VersionConstraint versionConstraint) {
        this.preferredVersion = nullToEmpty(versionConstraint.getPreferredVersion());
        this.strictVersion = nullToEmpty(versionConstraint.getStrictVersion());
        this.rejectedVersions.addAll(versionConstraint.getRejectedVersions());
    }

    public DefaultMutableVersionConstraint(String version) {
        prefer(version);
    }

    public DefaultMutableVersionConstraint(String version, boolean strict) {
        prefer(version);
        if (strict) {
            strictly(version);
        }
    }

    public DefaultMutableVersionConstraint(String version, List<String> rejects) {
        prefer(version);
        this.rejectedVersions.addAll(rejects);
    }

    @Override
    public ImmutableVersionConstraint asImmutable() {
        return new DefaultImmutableVersionConstraint(preferredVersion, strictVersion, rejectedVersions, branch);
    }

    @Nullable
    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public String getPreferredVersion() {
        return preferredVersion;
    }

    @Override
    public void prefer(String version) {
        this.preferredVersion = nullToEmpty(version);
        this.strictVersion = "";
        this.rejectedVersions.clear();
    }

    @Override
    public String getStrictVersion() {
        return strictVersion;
    }

    @Override
    public void strictly(String version) {
        prefer(version);
        this.strictVersion = nullToEmpty(version);
    }

    @Override
    public void reject(String... versions) {
        if (versions.length==0) {
            throw new InvalidUserDataException("The 'reject' clause requires at least one rejected version");
        }
        Collections.addAll(rejectedVersions, versions);
    }

    @Override
    public void rejectAll() {
        this.preferredVersion = "";
        this.rejectedVersions.clear();
        this.rejectedVersions.add("+");
    }

    @Override
    public List<String> getRejectedVersions() {
       return rejectedVersions;
    }
}
