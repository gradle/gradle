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

package org.gradle.internal.configuration;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.event.ActionInvocationHandler;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;

public final class LifecycleListenerBuildOperations {

    // takes Object as we have generic addListener methods in the Gradle interface
    public static Object maybeDecorate(Object listener, BuildOperationExecutor buildOperationExecutor) {
        // can be extended to support other types like BuildListener
        // currently these are implemented as interface-specific wrappers, but we could potentially
        // turn them into dynamic proxies if that gets too unwieldy

        // we'd also put a check for an internal marker interface to recognise internal callers here
        if (listener instanceof ProjectEvaluationListener) {
            return new BuildOperationEmittingProjectEvaluationListener(
                (ProjectEvaluationListener) listener,
                buildOperationExecutor,
                parentBuildOperationId(buildOperationExecutor));
        }
        return listener;
    }

    // For wrapping Closure-based listeners. We don't look for any internal impls etc as we don't expect internal
    // callers to be registering closures.
    public static Dispatch<MethodInvocation> decorate(Closure<?> closure, String name, BuildOperationExecutor buildOperationExecutor) {
        return new LifecycleListenerBuildOperationDispatch(
            new ClosureBackedMethodInvocationDispatch(name, closure),
            buildOperationExecutor,
            parentBuildOperationId(buildOperationExecutor)
        );
    }

    // For wrapping Action-based listeners
    public static Dispatch<MethodInvocation> maybeDecorate(Action<?> action, String name, BuildOperationExecutor buildOperationExecutor) {
        ActionInvocationHandler handler = new ActionInvocationHandler(name, action);
        // check for internal implementation details that we don't want to emit ops for
        if (action instanceof InternalAction) {
            return handler;
        } else {
            return new LifecycleListenerBuildOperationDispatch(
                handler,
                buildOperationExecutor,
                parentBuildOperationId(buildOperationExecutor)
            );
        }
    }

    private static Long parentBuildOperationId(BuildOperationExecutor buildOperationExecutor) {
        return buildOperationExecutor.getCurrentOperation().getId().getId();
    }

    private LifecycleListenerBuildOperations() {
    }
}
