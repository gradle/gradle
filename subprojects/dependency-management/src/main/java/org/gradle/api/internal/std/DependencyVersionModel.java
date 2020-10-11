/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

public class DependencyVersionModel implements Serializable {
    private final String preferredVersion;
    private final String requiredVersion;
    private final String strictlyVersion;
    private final List<String> rejectedVersions;
    private final Boolean rejectAll;
    private final int hashCode;

    public DependencyVersionModel(@Nullable String preferredVersion,
                                  @Nullable String requiredVersion,
                                  @Nullable String strictlyVersion,
                                  @Nullable List<String> rejectedVersions,
                                  @Nullable Boolean rejectAll) {
        this.preferredVersion = preferredVersion;
        this.requiredVersion = requiredVersion;
        this.strictlyVersion = strictlyVersion;
        this.rejectedVersions = rejectedVersions;
        this.rejectAll = rejectAll;
        this.hashCode = doComputeHashCode();
    }

    public String getPreferredVersion() {
        return preferredVersion;
    }

    public String getRequiredVersion() {
        return requiredVersion;
    }

    public String getStrictlyVersion() {
        return strictlyVersion;
    }

    public List<String> getRejectedVersions() {
        return rejectedVersions;
    }

    public Boolean getRejectAll() {
        return rejectAll;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DependencyVersionModel that = (DependencyVersionModel) o;

        if (preferredVersion != null ? !preferredVersion.equals(that.preferredVersion) : that.preferredVersion != null) {
            return false;
        }
        if (requiredVersion != null ? !requiredVersion.equals(that.requiredVersion) : that.requiredVersion != null) {
            return false;
        }
        if (strictlyVersion != null ? !strictlyVersion.equals(that.strictlyVersion) : that.strictlyVersion != null) {
            return false;
        }
        if (rejectedVersions != null ? !rejectedVersions.equals(that.rejectedVersions) : that.rejectedVersions != null) {
            return false;
        }
        return rejectAll != null ? rejectAll.equals(that.rejectAll) : that.rejectAll == null;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "DependencyVersionModel{" +
            "preferredVersion='" + preferredVersion + '\'' +
            ", requiredVersion='" + requiredVersion + '\'' +
            ", strictlyVersion='" + strictlyVersion + '\'' +
            ", rejectedVersions=" + rejectedVersions +
            ", rejectAll=" + rejectAll +
            ", hashCode=" + hashCode +
            '}';
    }

    private int doComputeHashCode() {
        int result = preferredVersion != null ? preferredVersion.hashCode() : 0;
        result = 31 * result + (requiredVersion != null ? requiredVersion.hashCode() : 0);
        result = 31 * result + (strictlyVersion != null ? strictlyVersion.hashCode() : 0);
        result = 31 * result + (rejectedVersions != null ? rejectedVersions.hashCode() : 0);
        result = 31 * result + (rejectAll != null ? rejectAll.hashCode() : 0);
        return result;
    }
}
