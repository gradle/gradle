/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
/**
 * A base runner for features hidden behind a flag, convenient for executing tests with the flag on or off.
 * If a test only makes sense if the feature is enabled, then it needs to be annotated with {@link RequiresFeatureEnabled}.
 */
@CompileStatic
abstract class BehindFlagFeatureRunner extends AbstractMultiTestRunner {
    final String systemProperty
    final String featureDescription

    BehindFlagFeatureRunner(Class<?> target, String systemProperty, String featureDescription) {
        super(target)
        // Ensure that the system property is propagated to forked Gradle executions
        AbstractGradleExecuter.propagateSystemProperty(systemProperty)
        this.systemProperty = systemProperty
        this.featureDescription = featureDescription
    }

    @Override
    protected void createExecutions() {
        // Run the test once with early dependency forced and once without
        add(new FeatureExecution(true))
        if (!target.annotations*.class.any { RequiresFeatureEnabled.isAssignableFrom(it) }) {
            add(new FeatureExecution(false))
        }
    }

    private class FeatureExecution extends AbstractMultiTestRunner.Execution {
        final boolean featureEnabled

        public FeatureExecution(boolean featureEnabled) {
            this.featureEnabled = featureEnabled
        }

        @Override
        protected String getDisplayName() {
            return featureEnabled ? "with ${featureDescription}" : "without ${featureDescription}"
        }

        @Override
        protected void before() {
            System.setProperty(systemProperty, String.valueOf(featureEnabled))
        }

        @Override
        protected void after() {
            System.properties.remove(systemProperty)
        }
    }
}
