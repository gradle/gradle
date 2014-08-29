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

import org.gradle.platform.base.ComponentSpecIdentifier;

/**
 * An identifier for a component that is built as part of the current build.
 */
public class DefaultComponentSpecIdentifier implements ComponentSpecIdentifier {
    private final String projectPath;
    private final String name;

    public DefaultComponentSpecIdentifier(String projectPath, String name) {
        this.projectPath = projectPath;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getProjectPath() {
        return projectPath;
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
        return name.equals(that.name) && projectPath.equals(that.projectPath);

    }

    @Override
    public int hashCode() {
        int result = projectPath.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }
}
