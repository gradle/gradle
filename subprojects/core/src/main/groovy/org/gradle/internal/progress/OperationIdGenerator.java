/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.progress;

import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;

/**
 * Generates operation ids used by the progress event infrastructure.
 */
public final class OperationIdGenerator {

    /**
     * Generates an operation id for the given task.
     *
     * @param task the task operation
     * @return the operation id
     */
    public static Object generateId(Task task) {
        return generateId(task.getProject().getGradle()) + "-" + task.getPath();
    }

    /**
     * Generates an operation id for the given Gradle instance.
     *
     * @param gradle the Gradle instance
     * @return the operation id
     */
    public static Object generateId(Gradle gradle) {
        return gradle == null ? null : String.valueOf(System.identityHashCode(gradle));
    }

}
