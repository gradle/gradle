/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshot;
import org.gradle.api.internal.tasks.compile.incremental.classpath.CurrentClasspathSnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.io.File;
import java.util.Collection;

public class CurrentCompilation {
    private final JavaCompileSpec spec;
    private final CurrentClasspathSnapshotter classpathSnapshotter;

    public CurrentCompilation(JavaCompileSpec spec, CurrentClasspathSnapshotter classpathSnapshotter) {
        this.spec = spec;
        this.classpathSnapshotter = classpathSnapshotter;
    }

    public Collection<File> getAnnotationProcessorPath() {
        return spec.getAnnotationProcessorPath();
    }

    public DependentsSet getDependentsOfClasspathChanges(PreviousCompilation previous) {
        ClasspathSnapshot currentClasspath = getClasspath();
        ClasspathSnapshot previousClasspath = previous.getClasspath();
        if (previousClasspath == null) {
            return DependentsSet.dependencyToAll("classpath data of previous compilation is incomplete");
        }
        ClasspathSnapshot.ClasspathChanges classpathChanges = currentClasspath.getChangesSince(previousClasspath);
        return previous.getDependents(classpathChanges);
    }

    private ClasspathSnapshot getClasspath() {
        return new ClasspathSnapshot(classpathSnapshotter.getClasspathSnapshot(Iterables.concat(spec.getCompileClasspath(), spec.getModulePath())));
    }

}
