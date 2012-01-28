/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.plugins.quality;

import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.VerificationTask;

/**
 * A {@code CodeQualityTask} performs some code quality checking on source files.
*/
public class CodeQualityTask extends SourceTask implements VerificationTask {

    private boolean ignoreFailures;
    private boolean displayViolations = true;

    /**
     * {@inheritDoc}
     */
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * {@inheritDoc}
     */
    public CodeQualityTask setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
        return this;
    }

    /**
     * Specifies whether the build should display violations on the console or not.
     *
     * @param displayViolations false to suppress console output, true to display violations on the console. The default is true.
     * @return this
     */
    public VerificationTask setDisplayViolations(boolean displayViolations) {
        this.displayViolations = displayViolations;
        return this;
    }

    /**
     * Specifies whether the build should display violations on the console or not.
     *
     * @return false, to suppress console output, true, to display violations on the console. The default is true.
     */
    public boolean isDisplayViolations() {
        return displayViolations;
    }


}
