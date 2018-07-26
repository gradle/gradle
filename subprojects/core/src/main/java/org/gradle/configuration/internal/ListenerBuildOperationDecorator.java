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
import org.gradle.api.Action;
import org.gradle.api.invocation.Gradle;

/**
 * Decorates registered Action, Closure and known interface listeners registered by configuration code,
 * to emit build operations around each execution.
 */
public interface ListenerBuildOperationDecorator {

    /**
     * Decorates an action to emit an ExecuteListenerBuildOperationType build operation when executing.
     *
     * <p>Does not decorate any action that implements {@link org.gradle.internal.InternalListener}</p>
     *
     * @param name the name of the listener, e.g. projectsLoaded
     * @param action the action to decorate
     * @param <T> The action's target type
     * @return a decorated Action, or the original if we don't need to decorate this one
     */
    <T> Action<T> decorate(String name, Action<T> action);

    /**
     * Decorates a closure to emit an ExecuteListenerBuildOperationType build operation when executing.
     *
     * <p>Does not decorate any listener that implements {@link org.gradle.internal.InternalListener}</p>
     *
     * @param name the name of the listener, e.g. projectsLoaded
     * @param closure the closure to decorate
     * @param <T> The Closure's generic type
     * @return a decorated Closure, or the original if we don't need to decorate this one
     */
    <T> Closure<T> decorate(String name, Closure<T> closure);

    /**
     * Decorates a known listener type to emit an ExecuteListenerBuildOperationType build operation when executing.
     *
     * <p>Supports decorating BuildListener, ProjectEvaluationListener and TaskExecutionGraphListener listeners</p>
     *
     * <p>Does not decorate any listener that implements {@link org.gradle.internal.InternalListener}</p>
     *
     * @param cls the type of the listener
     * @param listener the listener
     * @param <T> The listener type
     * @return a decorated listener, or the original if we don't need to decorate this one
     */
    <T> T decorate(Class<T> cls, T listener);

    /**
     * Decorates a listener of unknown type, generally registered using {@link Gradle#addListener}, to emit an ExecuteListenerBuildOperationType build operation when executing.
     *
     * <p>
     *     Supports decorating BuildListener, ProjectEvaluationListener and TaskExecutionGraphListener listeners. Note that a registered listener
     *     may implement more than one of these - the returned listener will implement all interfaces that the passed-in listener does.
     * </p>
     *
     * <p>Does not decorate any listener that implements {@link org.gradle.internal.InternalListener}</p>
     *
     * @param listener the listener
     * @return a decorated listener, or the original if we don't need to decorate this one
     */
    Object decorateUnknownListener(Object listener);

    /**
     * Used by script and plugin build operation emitting code to allocate an id, that is then
     * also emitted in the listener execution build operation to allow a build op consumer to
     * determine the registering script or plugin for a listener execution.
     */
    long allocateApplicationId();

    /**
     * Used by script and plugin build operation emitting code to mark the start of a script or plugin application.
     */
    void startApplication(long id);

    /**
     * Used by script and plugin build operation emitting code to mark the end of a script or plugin application.
     */
    void finishApplication(long id);

}
