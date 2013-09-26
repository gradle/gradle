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
package org.gradle.language.java.internal;

import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.internal.AbstractLanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.jvm.Classpath;

import java.util.HashSet;
import java.util.Set;

public class DefaultJavaSourceSet extends AbstractLanguageSourceSet implements JavaSourceSet {
    private final Classpath compileClasspath;

    public DefaultJavaSourceSet(String name, SourceDirectorySet source, Classpath compileClasspath, FunctionalSourceSet parent) {
        super(name, parent, "Java source", source);
        this.compileClasspath = compileClasspath;
    }

    public Classpath getCompileClasspath() {
        return compileClasspath;
    }


    public TaskDependency getBuildDependencies() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                Set<Task> dependencies = new HashSet<Task>();
                dependencies.addAll(compileClasspath.getBuildDependencies().getDependencies(task));
                dependencies.addAll(getSource().getBuildDependencies().getDependencies(task));
                return dependencies;
            }
        };
    }
}
