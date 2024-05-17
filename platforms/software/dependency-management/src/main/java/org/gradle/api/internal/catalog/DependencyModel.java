/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog;

import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;

import javax.annotation.Nullable;

public class DependencyModel extends AbstractContextAwareModel {
    private final String group;
    private final String name;
    private final ImmutableVersionConstraint version;
    private final String versionRef;
    private final int hashCode;

    public DependencyModel(String group,
                           String name,
                           @Nullable String versionRef,
                           ImmutableVersionConstraint version,
                           @Nullable String context) {
        super(context);
        this.group = group;
        this.name = name;
        this.version = version;
        this.versionRef = versionRef;
        this.hashCode = doComputeHashCode();
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public ImmutableVersionConstraint getVersion() {
        return version;
    }

    @Nullable
    public String getVersionRef() {
        return versionRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DependencyModel that = (DependencyModel) o;

        if (!group.equals(that.group)) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        if (version != null ? !version.equals(that.version) : that.version != null) {
            return false;
        }
        return versionRef != null ? versionRef.equals(that.versionRef) : that.versionRef == null;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int doComputeHashCode() {
        int result = group.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (versionRef != null ? versionRef.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "dependency {" +
            "group='" + group + '\'' +
            ", name='" + name + '\'' +
            ", version='" + version + '\'' +
            '}';
    }
}
