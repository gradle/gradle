/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public class DistributionIntegrationTestRunner extends BlockJUnit4ClassRunner {
    private static final String IGNORE_SYS_PROP = "org.gradle.integtest.ignore";

    public DistributionIntegrationTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    public void run(final RunNotifier notifier) {
        if (System.getProperty(IGNORE_SYS_PROP) != null) {
            notifier.fireTestIgnored(Description.createTestDescription(getTestClass().getJavaClass(),
                    "System property to ignore integration tests is set."));
        } else {
            super.run(notifier);
        }
    }
}
