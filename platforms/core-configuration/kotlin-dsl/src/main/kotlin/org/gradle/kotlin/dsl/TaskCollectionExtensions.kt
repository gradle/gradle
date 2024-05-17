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

package org.gradle.kotlin.dsl

import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection


/**
 * Returns a collection containing the objects in this collection of the given type. The
 * returned collection is live, so that when matching objects are later added to this
 * collection, they are also visible in the filtered collection.
 *
 * @param S The type of objects to find.
 * @return The matching objects. Returns an empty collection if there are no such objects
 * in this collection.
 * @see [TaskCollection.withType]
 */
inline fun <reified S : Task> TaskCollection<in S>.withType(): TaskCollection<S> =
    withType(S::class.java)
