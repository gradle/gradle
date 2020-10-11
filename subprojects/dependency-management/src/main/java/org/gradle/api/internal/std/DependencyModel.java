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

public class DependencyModel implements Serializable {
    private final String group;
    private final String name;
    private final DependencyVersionModel version;
    private final int hashCode;

    public DependencyModel(String group,
                           String name,
                           @Nullable DependencyVersionModel version) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.hashCode = doComputeHashCode();
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public DependencyVersionModel getVersion() {
        return version;
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
        return version != null ? version.equals(that.version) : that.version == null;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int doComputeHashCode() {
        int result = group.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DependencyData{" +
            "group='" + group + '\'' +
            ", name='" + name + '\'' +
            ", version='" + version + '\'' +
            '}';
    }
}
