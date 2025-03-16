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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * A service that collects problems of some type during the execution of Gradle for a build tree and reports them to the user
 * in some form.
 */

@ServiceScope({Scope.Global.class, Scope.BuildTree.class})
public interface ProblemReporter {
    interface ProblemConsumer {
        void accept(Throwable throwable);
    }

    /**
     * A stable identifier for this reporter. Reporters are ordered by id before {@link #report(File, ProblemConsumer)} is called, so
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
    void report(File reportDir, ProblemConsumer validationFailures);
}
