/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.jacoco.tasks.rules;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import java.math.BigDecimal;

/**
 * Defines a Jacoco rule limit.
 *
 * @since 3.4
 */
public interface JacocoLimit {

    /**
     * The counter that applies to the limit as defined by
     * <a href="http://www.eclemma.org/jacoco/trunk/doc/api/org/jacoco/core/analysis/ICoverageNode.CounterEntity.html">org.jacoco.core.analysis.ICoverageNode.CounterEntity</a>.
     * Valid values are INSTRUCTION, LINE, BRANCH, COMPLEXITY, METHOD and CLASS. Defaults to INSTRUCTION.
     */
    @Input
    @ReplacesEagerProperty
    Property<String> getCounter();

    /**
     * The value that applies to the limit as defined by
     * <a href="http://www.eclemma.org/jacoco/trunk/doc/api/org/jacoco/core/analysis/ICounter.CounterValue.html">org.jacoco.core.analysis.ICounter.CounterValue</a>.
     * Valid values are TOTALCOUNT, MISSEDCOUNT, COVEREDCOUNT, MISSEDRATIO and COVEREDRATIO. Defaults to COVEREDRATIO.
     */
    @Input
    @ReplacesEagerProperty
    Property<String> getValue();

    /**
     * Gets the minimum expected value for limit. Default to null.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    Property<BigDecimal> getMinimum();

    /**
     * Gets the maximum expected value for limit. Default to null.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    Property<BigDecimal> getMaximum();
}
