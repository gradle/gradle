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

public class VersionModel extends AbstractContextAwareModel {
    private final ImmutableVersionConstraint version;

    public VersionModel(ImmutableVersionConstraint version, @Nullable String context) {
        super(context);
        this.version = version;
    }

    public ImmutableVersionConstraint getVersion() {
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

        VersionModel that = (VersionModel) o;

        return version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }
}
