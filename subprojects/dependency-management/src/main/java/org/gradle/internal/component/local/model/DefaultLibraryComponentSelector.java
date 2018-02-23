/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentSelector;

import javax.annotation.Nullable;

public class DefaultLibraryComponentSelector implements LibraryComponentSelector {
    private final String projectPath;
    private final String libraryName;
    private final String variant;

    public DefaultLibraryComponentSelector(String projectPath, String libraryName) {
        this(projectPath, libraryName, null);
    }

    public DefaultLibraryComponentSelector(String projectPath, String libraryName, String variant) {
        assert !Strings.isNullOrEmpty(projectPath) : "project path cannot be null or empty";
        this.projectPath = projectPath;
        this.libraryName = Strings.emptyToNull(libraryName);
        this.variant = variant;
    }

    @Override
    public String getDisplayName() {
        String txt;
        if (Strings.isNullOrEmpty(libraryName)) {
            txt = "project '" + projectPath + "'";
        } else if (Strings.isNullOrEmpty(variant)) {
            txt = "project '" + projectPath + "' library '" + libraryName + "'";
        } else {
            txt = "project '" + projectPath + "' library '" + libraryName + "' binary '" + variant + "'";
        }
        return txt;
    }

    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public String getLibraryName() {
        return libraryName;
    }

    @Nullable
    @Override
    public String getVariant() {
        return variant;
    }

    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof LibraryBinaryIdentifier) {
            LibraryBinaryIdentifier projectComponentIdentifier = (LibraryBinaryIdentifier) identifier;
            return Objects.equal(projectComponentIdentifier.getProjectPath(), projectPath)
                && Objects.equal(projectComponentIdentifier.getLibraryName(), libraryName)
                && Objects.equal(projectComponentIdentifier.getVariant(), variant);
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultLibraryComponentSelector that = (DefaultLibraryComponentSelector) o;
        return Objects.equal(projectPath, that.projectPath)
            && Objects.equal(libraryName, that.libraryName)
            && Objects.equal(variant, that.variant);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(projectPath, libraryName, variant);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
