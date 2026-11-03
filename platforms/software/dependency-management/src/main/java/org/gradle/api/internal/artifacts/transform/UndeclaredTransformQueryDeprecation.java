/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.model.DomainObjectContext;
import org.jspecify.annotations.Nullable;

/**
 * Deprecates querying the output of an artifact transform from the action of another artifact transform,
 * without declaring it as an input.
 */
public final class UndeclaredTransformQueryDeprecation {

    /**
     * The implementation of the transform action the current thread runs, if any.
     */
    private static final ThreadLocal<@Nullable Class<?>> RUNNING_ACTION = new ThreadLocal<>();

    private UndeclaredTransformQueryDeprecation() {
    }

    /**
     * Runs the given action as the action of a transform, so that the outputs it queries are deprecated.
     */
    public static void runAsTransformAction(Class<?> implementationClass, Runnable action) {
        Class<?> previousAction = RUNNING_ACTION.get();
        RUNNING_ACTION.set(implementationClass);
        try {
            action.run();
        } finally {
            RUNNING_ACTION.set(previousAction);
        }
    }

    /**
     * Deprecates querying the output of the given transform, if the current thread runs the action of a transform.
     */
    public static void deprecateIfFromTransformAction(Class<?> implementationClass, DomainObjectContext owner) {
        Class<?> runningAction = RUNNING_ACTION.get();
        if (runningAction != null) {
            // Transform actions execute on parallel worker threads, which to avoid deadlocking, are not permitted to acquire locks.
            // If a transform action queries the output of _another_ transform, that usage of that other transform was not declared,
            // and we therefore may not have isolated its parameters before this parallel step. To avoid "forcing" access to the
            // parameter's model, we deprecate this scenario. We do so whenever the output is queried, rather than when the parameters
            // are isolated, as whether something else isolated them first depends on scheduling.
            DeprecationLogger.deprecateBehaviour("Querying the output of one artifact transform from the action of another, without declaring that output as an input of the other.")
                .withContext(String.format(
                    "The output of artifact transform %s, registered in %s, was queried by the action of artifact transform %s, which did not declare it as an input.",
                    implementationClass.getName(), owner.getDisplayName(), runningAction.getName()
                ))
                .withAdvice(String.format(
                    "Declare the output of artifact transform %s as an input of artifact transform %s, using a parameter annotated with @InputFiles.",
                    implementationClass.getName(), runningAction.getName()
                ))
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(9, "querying_transform_output_from_transform_action")
                .nagUser();
        }
    }
}
