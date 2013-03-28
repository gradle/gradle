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
package org.gradle.language.jvm.internal;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.jvm.ResourceSet;

public class DefaultResourceSet implements ResourceSet {
    private final String name;
    private final SourceDirectorySet source;
    private final FunctionalSourceSet parent;

    public DefaultResourceSet(String name, SourceDirectorySet source, FunctionalSourceSet parent) {
        this.name = name;
        this.source = source;
        this.parent = parent;
    }

    public SourceDirectorySet getSource() {
        return source;
    }

    public FunctionalSourceSet getParent() {
        return parent;
    }

    public TaskDependency getBuildDependencies() {
        return source.getBuildDependencies();
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return String.format("%s/%s source set", parent.getName(), name);
    }
}
