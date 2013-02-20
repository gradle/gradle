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
package org.gradle.api.internal.tasks;

import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.FunctionalSourceSet;
import org.gradle.api.tasks.JavaSourceSet;
import org.gradle.api.tasks.TaskDependency;

import java.util.HashSet;
import java.util.Set;

public class DefaultJavaSourceSet implements JavaSourceSet {
    private final String name;
    private final SourceDirectorySet source;
    private final Classpath compileClasspath;
    private final FunctionalSourceSet parent;

    public DefaultJavaSourceSet(String name, SourceDirectorySet source, Classpath compileClasspath, FunctionalSourceSet parent) {
        this.name = name;
        this.source = source;
        this.compileClasspath = compileClasspath;
        this.parent = parent;
    }

    public Classpath getCompileClasspath() {
        return compileClasspath;
    }

    public SourceDirectorySet getSource() {
        return source;
    }

    public FunctionalSourceSet getParent() {
        return parent;
    }

    public TaskDependency getBuildDependencies() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                Set<Task> dependencies = new HashSet<Task>();
                dependencies.addAll(compileClasspath.getBuildDependencies().getDependencies(task));
                dependencies.addAll(source.getBuildDependencies().getDependencies(task));
                return dependencies;
            }
        };
    }

    public String getName() {
        return name;
    }


    public String toString() {
        return String.format("%s/%s source set", parent.getName(), name);
    }
}
