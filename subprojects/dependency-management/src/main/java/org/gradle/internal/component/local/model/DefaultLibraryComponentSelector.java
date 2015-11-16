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

public class DefaultLibraryComponentSelector implements LibraryComponentSelector {
    private final String projectPath;
    private final String libraryName;

    public DefaultLibraryComponentSelector(String projectPath, String libraryName) {
        assert !Strings.isNullOrEmpty(projectPath) : "project path cannot be null or empty";
        this.projectPath = projectPath;
        this.libraryName = Strings.emptyToNull(libraryName);
    }

    @Override
    public String getDisplayName() {
        String txt;
        if (Strings.isNullOrEmpty(libraryName)) {
            txt = String.format("project '%s'", projectPath);
        } else {
            txt = String.format("project '%s' library '%s'", projectPath, libraryName);
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

    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof LibraryBinaryIdentifier) {
            LibraryBinaryIdentifier projectComponentIdentifier = (LibraryBinaryIdentifier) identifier;
            return projectPath.equals(projectComponentIdentifier.getProjectPath())
                && projectComponentIdentifier.getLibraryName().equals(libraryName);
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
            && Objects.equal(libraryName, that.libraryName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(projectPath, libraryName);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
