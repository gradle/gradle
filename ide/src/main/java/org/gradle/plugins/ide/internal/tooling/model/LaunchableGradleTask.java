/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.model;

import org.gradle.TaskExecutionRequest;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.protocol.InternalLaunchable;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class LaunchableGradleTask implements Serializable, InternalLaunchable, TaskExecutionRequest {

    private String path;
    private String name;
    private String description;
    private String displayName;
    private String group;
    private boolean isPublic;
    private DefaultProjectIdentifier projectIdentifier;

    public String getPath() {
        return path;
    }

    public LaunchableGradleTask setPath(String path) {
        this.path = path;
        return this;
    }

    public String getName() {
        return name;
    }

    public LaunchableGradleTask setName(String name) {
        this.name = name;
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LaunchableGradleTask setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public LaunchableGradleTask setDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public List<String> getArgs() {
        return Collections.singletonList(path);
    }

    @Override
    public String getProjectPath() {
        return null;
    }

    public String getGroup() {
        return group;
    }

    public LaunchableGradleTask setGroup(String group) {
        this.group = group;
        return this;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public LaunchableGradleTask setPublic(boolean isPublic) {
        this.isPublic = isPublic;
        return this;
    }

    @Override
    public File getRootDir() {
        return projectIdentifier.getBuildIdentifier().getRootDir();
    }

    public LaunchableGradleTask setProjectIdentifier(DefaultProjectIdentifier projectIdentifier) {
        this.projectIdentifier = projectIdentifier;
        return this;
    }

    public DefaultProjectIdentifier getProjectIdentifier() {
        return projectIdentifier;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{path='" + path + "',public=" + isPublic + "}";
    }
}
