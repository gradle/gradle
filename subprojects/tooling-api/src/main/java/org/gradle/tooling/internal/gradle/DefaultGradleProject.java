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

package org.gradle.tooling.internal.gradle;

import org.gradle.tooling.internal.protocol.InternalGradleProject;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DefaultGradleProject<T> extends PartialGradleProject implements InternalGradleProject, Serializable, GradleProjectIdentity {
    private DefaultGradleScript buildScript = new DefaultGradleScript();
    private List<T> tasks = new LinkedList<T>();

    @Override
    public DefaultGradleProject<T> setName(String name) {
        super.setName(name);
        return this;
    }

    @Override
    public DefaultGradleProject<T> setPath(String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public DefaultGradleProject<T> setDescription(String description) {
        super.setDescription(description);
        return this;
    }

    @Override
    public DefaultGradleProject<T> setChildren(List<? extends PartialGradleProject> children) {
        super.setChildren(children);
        return this;
    }

    public Collection<T> getTasks() {
        return tasks;
    }

    public DefaultGradleProject<T> setTasks(List<T> tasks) {
        this.tasks = tasks;
        return this;
    }

    public DefaultGradleScript getBuildScript() {
        return buildScript;
    }

    public File getProjectDirectory() {
        throw new RuntimeException("ProjectVersion3 methods are deprecated.");
    }

    @Override
    public DefaultGradleProject<T> findByPath(String path) {
        return (DefaultGradleProject<T>) super.findByPath(path);
    }
}
