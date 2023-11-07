/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.steps;

import org.gradle.internal.execution.IncrementalUnitOfWork;
import org.gradle.internal.execution.UnitOfWork;

public class ChoosePipelineStep<C extends IdentityContext, R extends Result> implements Step<C, R> {

    private final Step<? super IdentityContext, ? extends R> incrementalPipeline;
    private final Step<? super IdentityContext, ? extends R> nonIncrementalPipeline;

    public ChoosePipelineStep(
        Step<? super IdentityContext, ? extends R> incrementalPipeline,
        Step<? super IdentityContext, ? extends R> nonIncrementalPipeline
    ) {
        this.incrementalPipeline = incrementalPipeline;
        this.nonIncrementalPipeline = nonIncrementalPipeline;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        if (work instanceof IncrementalUnitOfWork) {
            return incrementalPipeline.execute(work, context);
        } else {
            return nonIncrementalPipeline.execute(work, context);
        }
    }
}
