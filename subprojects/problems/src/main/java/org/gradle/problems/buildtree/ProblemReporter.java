/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.problems.buildtree;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.util.function.Consumer;

/**
 * Notifies the user of problems of some type collected during the execution of a build against a build tree.
 */
@ServiceScope(Scopes.BuildTree.class)
public interface ProblemReporter {
    /**
     * A stable identifier for this reporter. Reporters are ordered by id before {@link #report(File, Consumer)} is called, so
     * that the output is generated in a stable order rather than in an order based on the order that implementations
     * are discovered.
     */
    String getId();

    /**
     * Notifies the build user of whatever problems have been collected. May report problems to the console, or generate a report
     * or fail with one or more exceptions, or all of the above.
     *
     * @param reportDir The base directory that reports can be generated into.
     * @param validationFailures Collects any validation failures.
     */
    void report(File reportDir, Consumer<? super Throwable> validationFailures);
}
