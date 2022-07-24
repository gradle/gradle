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

package org.gradle.api.internal.tasks;

import com.google.common.base.Strings;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;

/**
 * Decides whether a {@link org.gradle.api.Task} is a public task or not.
 */
public final class PublicTaskSpecification implements Spec<Task> {

    public static final Spec<Task> INSTANCE = new PublicTaskSpecification();

    private PublicTaskSpecification() {
    }

    @Override
    public boolean isSatisfiedBy(Task task) {
        return !Strings.isNullOrEmpty(task.getGroup());
    }

}
