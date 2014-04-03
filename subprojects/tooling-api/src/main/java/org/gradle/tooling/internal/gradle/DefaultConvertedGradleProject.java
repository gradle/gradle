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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DefaultConvertedGradleProject extends PartialGradleProject {
    private List<DefaultGradleTask> tasks = new LinkedList<DefaultGradleTask>();

    @Override
    public DefaultConvertedGradleProject setName(String name) {
        super.setName(name);
        return this;
    }

    @Override
    public DefaultConvertedGradleProject setPath(String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public DefaultConvertedGradleProject setDescription(String description) {
        super.setDescription(description);
        return this;
    }

    @Override
    public DefaultConvertedGradleProject setChildren(List<? extends PartialGradleProject> children) {
        super.setChildren(children);
        return this;
    }

    public Collection<DefaultGradleTask> getTasks() {
        return tasks;
    }

    public DefaultConvertedGradleProject setTasks(List<DefaultGradleTask> tasks) {
        this.tasks = tasks;
        return this;
    }
}
