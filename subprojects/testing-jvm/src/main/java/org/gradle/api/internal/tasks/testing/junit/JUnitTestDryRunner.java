/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;

public class JUnitTestDryRunner extends Runner {
    private final Runner runner;

    public JUnitTestDryRunner(Runner runner) {
        this.runner = runner;
    }

    @Override
    public Description getDescription() {
        return runner.getDescription();
    }

    @Override
    public void run(RunNotifier notifier) {
        dryRun(notifier, getDescription());
    }

    private void dryRun(RunNotifier notifier, Description description) {
        if (description.isSuite()) {
            notifier.fireTestSuiteStarted(description);

            for (Description child : description.getChildren()) {
                dryRun(notifier, child);
            }

            notifier.fireTestSuiteFinished(description);
        } else {
            notifier.fireTestStarted(description);
            notifier.fireTestFinished(description);
        }
    }
}
