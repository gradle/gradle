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
package org.gradle.jvm.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.jvm.Classpath;
import org.gradle.api.tasks.TaskDependency;

public class DefaultClasspath implements Classpath {
    private final FileCollection files;

    public DefaultClasspath(FileResolver fileResolver, TaskResolver taskResolver) {
        files = new DefaultConfigurableFileCollection(fileResolver, taskResolver);
    }

    public FileCollection getFiles() {
        return files;
    }

    public TaskDependency getBuildDependencies() {
        return files.getBuildDependencies();
    }
}
