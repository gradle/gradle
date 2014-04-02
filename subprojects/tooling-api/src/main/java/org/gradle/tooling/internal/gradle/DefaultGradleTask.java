/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.gradle;

import java.util.Collections;
import java.util.Set;

public class DefaultGradleTask implements TaskListingLaunchable {

    private String path;
    private String name;
    private String description;
    private String displayName;

    public String getPath() {
        return path;
    }

    public DefaultGradleTask setPath(String path) {
        this.path = path;
        return this;
    }

    public String getName() {
        return name;
    }

    public DefaultGradleTask setName(String name) {
        this.name = name;
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DefaultGradleTask setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public DefaultGradleTask setDescription(String description) {
        this.description = description;
        return this;
    }

    public Set<String> getTaskNames() {
        return Collections.singleton(getPath());
    }

    @Override
    public String toString() {
        return "GradleTask{"
                + "name='" + name + '\''
                + " path='" + path + '\''
                + '}';
    }
}
