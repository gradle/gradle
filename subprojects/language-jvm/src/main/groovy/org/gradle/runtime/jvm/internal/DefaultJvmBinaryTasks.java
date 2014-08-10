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

package org.gradle.runtime.jvm.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.runtime.jvm.JvmBinaryTasks;
import org.gradle.runtime.jvm.JvmLibraryBinarySpec;

public class DefaultJvmBinaryTasks extends DefaultDomainObjectSet<Task> implements JvmBinaryTasks {
    private final JvmLibraryBinarySpec binary;

    public DefaultJvmBinaryTasks(JvmLibraryBinarySpec binary) {
        super(Task.class);
        this.binary = binary;
    }

    public Task getBuild() {
        return binary.getBuildTask();
    }

    public Jar getJar() {
        DomainObjectSet<Jar> tasks = withType(Jar.class);
        if (tasks.size() == 0) {
            return null;
        }
        if (tasks.size() > 1) {
            throw new GradleException("Multiple task with type 'Jar' found.");
        }
        return tasks.iterator().next();
    }
}
