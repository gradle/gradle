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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the associated task can be safely executed in parallel with other tasks.
 * <p>
 * Tasks are not parallelizable by default because it is possible for tasks to interfere with each other in unsafe ways.
 * That is, it is possible for tasks to mutate shared data structures during their execution.
 * The presence of this annotation on task class declares that the task does not do this and is therefore parallelizable.
 * <p>
 * For a task to be safely parallelizable, it should not change any data that may be read by other tasks.
 * It should not, for example, update project extensions, other tasks or any other shared data.
 * It may change internal variables and properties of the task, the filesystem and other external resources.
 * <p>
 * This annotation is not inherited.
 * A task class that extends from another task class that declares itself to be parallel safe is not implicitly also parallel safe.
 * If the subclass is indeed parallel safe, it must also have this annotation.
 * <p>
 * Any task that has custom actions (i.e. ones added via {@link org.gradle.api.Task#doLast(org.gradle.api.Action)} or {@link org.gradle.api.Task#doFirst(org.gradle.api.Action)})
 * is not considered parallelizable even if its type carries this annotation.
 * This is because it cannot be known whether the added action is parallel safe or not.
 * <p>
 * Any two tasks that declare overlapping file system outputs will not be run in parallel with each other even if they carry this annotation.
 * In general tasks should not be configured to write to the same file or directory on the filesystem and they should only write into filesystem locations that are declared as their outputs for this reason.
 * Overlaps caused by descendants of output directories (e.g. a symlink pointing at an output of one task and being part of a directory declared as output of another task) are not being considered.
 * For such tasks a must run after ordering should be defined to ensure that they are executed serially.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Incubating
public @interface ParallelizableTask {
}
