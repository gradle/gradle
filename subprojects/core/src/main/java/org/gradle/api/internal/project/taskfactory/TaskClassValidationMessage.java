/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

public class TaskClassValidationMessage {
    private final String message;

    public TaskClassValidationMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public static TaskClassValidationMessage property(String name, String message) {
        return new TaskClassValidationMessage(String.format("property '%s' %s", name, message));
    }

    @Override
    public String toString() {
        return message;
    }
}
