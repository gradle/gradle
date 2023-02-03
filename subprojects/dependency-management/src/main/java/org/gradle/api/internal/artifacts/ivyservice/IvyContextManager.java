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

package org.gradle.api.internal.artifacts.ivyservice;

import javax.annotation.concurrent.ThreadSafe;
import org.apache.ivy.Ivy;
import org.gradle.api.Action;
import org.gradle.api.Transformer;

@ThreadSafe
@SuppressWarnings("overloads")
public interface IvyContextManager {
    /**
     * Executes the given action against an Ivy instance. Sets up the Ivy context before the action and cleans up at the end.
     *
     * <p>The Ivy instance of the calling thread is reused if the thread is already executing an action against an Ivy instance.
     */
    void withIvy(Action<? super Ivy> action);

    /**
     * Executes the given action against an Ivy instance and returns the result. Sets up the Ivy context before the action and cleans up at the end.
     *
     * <p>The Ivy instance of the calling thread is reused if the thread is already executing an action against an Ivy instance.
     */
    <T> T withIvy(Transformer<? extends T, ? super Ivy> action);
}
