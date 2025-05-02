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

package org.gradle.api.internal.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskDestroyables;
import org.gradle.internal.properties.PropertyVisitor;


/**
 * Note: this is currently not visible on {@link org.gradle.api.internal.TaskInternal} to avoid it leaking onto {@link org.gradle.api.internal.AbstractTask} and so on to the public API.
 */
public interface TaskDestroyablesInternal extends TaskDestroyables {

    void visitRegisteredProperties(PropertyVisitor visitor);

    FileCollection getRegisteredFiles();
}
