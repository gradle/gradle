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

package org.gradle.language.jvm.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.jvm.Classpath;

// Temporary Classpath implementation for new jvm component model
public class EmptyClasspath implements Classpath {
    @Override
    public FileCollection getFiles() {
        return ImmutableFileCollection.of();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }
}
