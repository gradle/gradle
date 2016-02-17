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
package org.gradle.api.internal.java;

import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.jvm.Classpath;
import org.gradle.language.base.internal.AbstractLanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.platform.base.DependencySpecContainer;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;
import org.gradle.platform.base.internal.DefaultDependencySpecContainer;

import java.util.HashSet;
import java.util.Set;

public class DefaultJavaSourceSet extends AbstractLanguageSourceSet implements JavaSourceSet {
    private final Classpath compileClasspath;

    public DefaultJavaSourceSet(ComponentSpecIdentifier componentIdentifier, SourceDirectorySet source, Classpath compileClasspath) {
        super(componentIdentifier, JavaSourceSet.class, source);
        this.compileClasspath = compileClasspath;
    }

    @Override
    protected String getLanguageName() {
        return "Java";
    }

    public Classpath getCompileClasspath() {
        return compileClasspath;
    }

    @Override
    public DependencySpecContainer getDependencies() {
        return new DefaultDependencySpecContainer();
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
