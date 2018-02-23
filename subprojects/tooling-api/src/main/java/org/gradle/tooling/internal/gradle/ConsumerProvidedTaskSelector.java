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

package org.gradle.tooling.internal.gradle;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.SortedSet;

/**
 * A consumer-side implementation of {@link org.gradle.tooling.model.TaskSelector}
 */
public class ConsumerProvidedTaskSelector implements TaskListingLaunchable, Serializable {
    private String name;
    private String displayName;
    private String description;
    private SortedSet<String> tasks;

    public String getName() {
        return name;
    }

    public ConsumerProvidedTaskSelector setName(String name) {
        this.name = name;
        return this;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public ConsumerProvidedTaskSelector setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ConsumerProvidedTaskSelector setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public SortedSet<String> getTaskNames() {
        return tasks;
    }

    public ConsumerProvidedTaskSelector setTaskNames(SortedSet<String> tasks) {
        this.tasks = tasks;
        return this;
    }

    @Override
    public String toString() {
        return "ConsumerProvidedTaskSelector{"
                + "name='" + name + "' "
                + "description='" + description + "'}";
    }
}
