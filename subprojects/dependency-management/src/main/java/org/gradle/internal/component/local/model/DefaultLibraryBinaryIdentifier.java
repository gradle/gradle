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
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;

public class DefaultLibraryBinaryIdentifier implements LibraryBinaryIdentifier {
    private static final Interner<DefaultLibraryBinaryIdentifier> INSTANCES_INTERNER = Interners.newWeakInterner();
    private final String projectPath;
    private final String libraryName;
    private final String variant;
    private final int hashCode;
    private String displayName;

    private DefaultLibraryBinaryIdentifier(String projectPath, String libraryName, String variant) {
        assert projectPath != null : "project path cannot be null";
        assert libraryName != null : "library name cannot be null";
        assert variant != null : "variant cannot be null";
        this.projectPath = projectPath;
        this.libraryName = libraryName;
        this.variant = variant;
        this.hashCode = calculateHashCode();
    }

    public static DefaultLibraryBinaryIdentifier of(String projectPath, String libraryName, String variant) {
        DefaultLibraryBinaryIdentifier instance = new DefaultLibraryBinaryIdentifier(projectPath, libraryName, variant);
        return INSTANCES_INTERNER.intern(instance);
    }

    public String getDisplayName() {
        if (displayName == null) {
            displayName = createDisplayName();
        }
        return displayName;
    }

    private String createDisplayName() {
        return "project '" + projectPath + "' library '" + libraryName + "' variant '" + variant + "'";
    }

    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public String getLibraryName() {
        return libraryName;
    }

    @Override
    public String getVariant() {
        return variant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultLibraryBinaryIdentifier that = (DefaultLibraryBinaryIdentifier) o;
        if (hashCode() != that.hashCode()) {
            return false;
        }
        return Objects.equal(projectPath, that.projectPath)
            && Objects.equal(libraryName, that.libraryName)
            && Objects.equal(variant, that.variant);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int calculateHashCode() {
        return Objects.hashCode(projectPath, libraryName, variant);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
