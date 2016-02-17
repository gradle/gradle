/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.base.Objects;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.util.Path;

/**
 * An identifier for a component that is built as part of the current build.
 */
public class DefaultComponentSpecIdentifier implements ComponentSpecIdentifier {
    private final DefaultComponentSpecIdentifier parent;
    private final String projectPath;
    private final String name;

    public DefaultComponentSpecIdentifier(String projectPath, String name) {
        this(projectPath, null, name);
    }

    private DefaultComponentSpecIdentifier(String projectPath, DefaultComponentSpecIdentifier parent, String name) {
        this.projectPath = projectPath;
        this.name = name;
        this.parent = parent;
    }

    @Override
    public ComponentSpecIdentifier child(String name) {
        return new DefaultComponentSpecIdentifier(projectPath, this, name);
    }

    @Nullable
    @Override
    public ComponentSpecIdentifier getParent() {
        return parent;
    }

    @Override
    public Path getPath() {
        return Path.path(getQualifiedPath());
    }

    private String getQualifiedPath() {
        return parent == null ? name : parent.getQualifiedPath() + Project.PATH_SEPARATOR + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getProjectScopedName() {
        return parent == null ? name : parent.getProjectScopedName() + StringUtils.capitalize(name);
    }

    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public String toString() {
        return getQualifiedPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultComponentSpecIdentifier)) {
            return false;
        }

        DefaultComponentSpecIdentifier that = (DefaultComponentSpecIdentifier) o;
        return name.equals(that.name) && projectPath.equals(that.projectPath) && Objects.equal(parent, that.parent);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        if (parent != null) {
            result = 31 * result + parent.hashCode();
        } else {
            result = 31 * result + projectPath.hashCode();
        }
        return result;
    }
}
