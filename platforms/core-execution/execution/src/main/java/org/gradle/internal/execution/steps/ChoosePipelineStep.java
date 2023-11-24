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

import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.MutableUnitOfWork;
import org.gradle.internal.execution.UnitOfWork;

public class ChoosePipelineStep<C extends IdentityContext, R extends Result> implements Step<C, R> {

    private final Step<? super IdentityContext, ? extends R> immutablePipeline;
    private final Step<? super IdentityContext, ? extends R> mutablePipeline;

    public ChoosePipelineStep(
        Step<? super IdentityContext, ? extends R> immutablePipeline, Step<? super IdentityContext, ? extends R> mutablePipeline
    ) {
        this.immutablePipeline = immutablePipeline;
        this.mutablePipeline = mutablePipeline;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        if (work instanceof ImmutableUnitOfWork) {
            return immutablePipeline.execute(work, context);
        } else if (work instanceof MutableUnitOfWork) {
            return mutablePipeline.execute(work, context);
        } else {
            throw new AssertionError("Invalid work type: " + work.getClass().getName());
        }
    }
}
