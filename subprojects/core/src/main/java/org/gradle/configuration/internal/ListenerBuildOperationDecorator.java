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

package org.gradle.configuration.internal;

import groovy.lang.Closure;
import org.gradle.BuildListener;
import org.gradle.api.Action;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.internal.InternalListener;

/**
 * Decorates listener functions/objects to fire {@link ExecuteListenerBuildOperationType} build operations when later executed.
 *
 * Works in conjunction with {@link UserCodeApplicationContext} to attach the current user code application ID
 * to the listener, in order to convey it as part of the operation details.
 * This allows tracking the listener back to the plugin or script that <i>registered</i> it.
 */
public interface ListenerBuildOperationDecorator {

    /**
     * Decorates an action listener.
     * <p>
     * Does not decorate any action that implements {@link InternalListener}.
     * Does not decorate if there is not currently a script or plugin being applied on the thread.
     *
     * @param name the name of the listener (e.g. projectsLoaded) - used in the operation description
     * @param action the action to decorate
     */
    <T> Action<T> decorate(String name, Action<T> action);

    /**
     * Decorates a closure listener.
     * <p>
     * Does not decorate any action that implements {@link InternalListener}.
     * Does not decorate if there is not currently a script or plugin being applied on the thread.
     *
     * @param name the name of the listener (e.g. projectsLoaded) - used in the operation description
     * @param closure the closure to decorate
     */
    <T> Closure<T> decorate(String name, Closure<T> closure);

    /**
     * Decorates a listener type object.
     * <p>
     * Supports decorating {@link BuildListener}, {@link ProjectEvaluationListener} and {@link TaskExecutionGraphListener} listeners
     * <p>
     * Does not decorate any action that implements {@link InternalListener}.
     * Does not decorate if there is not currently a script or plugin being applied on the thread.
     *
     * @param cls the type of the listener
     * @param listener the listener
     */
    <T> T decorate(Class<T> cls, T listener);

    /**
     * Decorates a listener of unknown type.
     * <p>
     * @see #decorate(Class, Object)
     */
    Object decorateUnknownListener(Object listener);


}
