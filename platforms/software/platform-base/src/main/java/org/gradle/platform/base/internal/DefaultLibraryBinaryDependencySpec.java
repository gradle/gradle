/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.platform.base.internal;

import com.google.common.base.Joiner;
import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.LibraryBinaryDependencySpec;

import java.util.Arrays;
import java.util.List;

public class DefaultLibraryBinaryDependencySpec implements LibraryBinaryDependencySpec {

    private final String projectPath;
    private final String libraryName;
    private final String variant;

    public DefaultLibraryBinaryDependencySpec(String projectPath, String libraryName, String variant) {
        if (libraryName == null || projectPath == null || variant == null) {
            throw new IllegalDependencyNotation("A direct library binary dependency must have all of project, library name and variant specified.");
        }
        this.libraryName = libraryName;
        this.projectPath = projectPath;
        this.variant = variant;
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
    public String getDisplayName() {
        List<String> parts = Arrays.asList(
            "project '" + getProjectPath() + "'",
            "library '" + getLibraryName() + "'",
            "variant '" + getVariant() + "'");
        return Joiner.on(' ').join(parts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultLibraryBinaryDependencySpec that = (DefaultLibraryBinaryDependencySpec) o;
        return ObjectUtils.equals(projectPath, that.projectPath)
            && ObjectUtils.equals(libraryName, that.libraryName)
            && ObjectUtils.equals(variant, that.variant);
    }

    @Override
    public int hashCode() {
        int result = ObjectUtils.hashCode(projectPath);
        result = 31 * result + ObjectUtils.hashCode(libraryName);
        result = 31 * result + ObjectUtils.hashCode(variant);
        return result;
    }

    public static DependencySpec of(LibraryBinaryIdentifier id) {
        return new DefaultLibraryBinaryDependencySpec(id.getProjectPath(), id.getLibraryName(), id.getVariant());
    }
}
