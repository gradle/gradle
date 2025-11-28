/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.internal.execution.MutableUnitOfWork;
import org.gradle.internal.execution.UnitOfWork;

// TODO Instead of this class, we could consider making Step generic in UnitOfWork type.
public abstract class MutableStep<C extends Context, R extends Result> implements Step<C, R> {
    @Override
    public final R execute(UnitOfWork work, C context) {
        return executeMutable((MutableUnitOfWork) work, context);
    }

    protected abstract R executeMutable(MutableUnitOfWork work, C context);
}
