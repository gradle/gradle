/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.instantiation.InstantiationScheme;

import javax.annotation.Nullable;

public interface ITaskFactory {
    ITaskFactory createChild(ProjectInternal project, InstantiationScheme instantiationScheme);

    /**
     * @param constructorArgs null == do not invoke constructor, empty == invoke constructor with no args, non-empty = invoke constructor with args
     */
    <S extends Task> S create(TaskIdentity<S> taskIdentity, @Nullable Object[] constructorArgs);
}
