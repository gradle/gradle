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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;

public final class OperationIdGenerator {

    public static String generateId(Task task) {
        if (task==null) {
            return null;
        }
        Project project = task.getProject();
        Gradle gradle = project!=null?project.getGradle():null;
        return generateId(gradle, task.getPath());
    }

    public static String generateId(Gradle gradle) {
        return gradle == null ? "" : String.valueOf(System.identityHashCode(gradle));
    }

    public static String generateId(Gradle gradle, String operationName) {
        return String.format("%s : %s", generateId(gradle), operationName);
    }

}
