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

package org.gradle.internal.buildtree;

import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.problems.buildtree.ProblemReporter;

import java.io.File;
import java.util.function.Consumer;

public class DeprecationsReporter implements ProblemReporter {
    @Override
    public String getId() {
        return "deprecations";
    }

    @Override
    public void report(File reportDir, Consumer<? super Throwable> validationFailures) {
        Throwable failure = DeprecationLogger.getDeprecationFailure();
        if (failure != null) {
            validationFailures.accept(failure);
        }
    }
}
