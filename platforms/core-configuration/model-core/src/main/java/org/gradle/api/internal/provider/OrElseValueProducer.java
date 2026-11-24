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

package org.gradle.api.internal.provider;

import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.evaluation.EvaluationContext;
import org.gradle.internal.evaluation.EvaluationOwner;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

class OrElseValueProducer implements ValueSupplier.ValueProducer {

    private final EvaluationOwner owner;
    private final ProviderInternal<?> left;
    @Nullable
    private final ProviderInternal<?> right;
    private final ValueSupplier.ValueProducer leftProducer;
    private final ValueSupplier.ValueProducer rightProducer;

    public OrElseValueProducer(EvaluationScopeContext context, ProviderInternal<?> left) {
        this(context, left, null, ValueSupplier.ValueProducer.unknown());
    }

    public OrElseValueProducer(EvaluationScopeContext context, ProviderInternal<?> left, ProviderInternal<?> right) {
        this(context, left, right, right.getProducer());
    }

    private OrElseValueProducer(EvaluationScopeContext context, ProviderInternal<?> left, @Nullable ProviderInternal<?> right, ValueSupplier.ValueProducer rightProducer) {
        this.owner = Objects.requireNonNull(context.getOwner(), "Cannot create a value producer outside of an evaluation scope.");
        this.left = left;
        this.right = right;
        this.leftProducer = left.getProducer();
        this.rightProducer = rightProducer;
    }

    @Override
    public boolean isKnown() {
        return leftProducer.isKnown()
            || rightProducer.isKnown();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        try (EvaluationScopeContext ignored = EvaluationContext.current().open(owner)) {
            if (mayHaveValue(left)) {
                if (leftProducer.isKnown()) {
                    leftProducer.visitDependencies(context);
                }
                return;
            }
            if (right != null && rightProducer.isKnown() && mayHaveValue(right)) {
                rightProducer.visitDependencies(context);
            }
        }
    }

    @Override
    public void visitGuardedDependencies(TaskDependencyResolveContext context) {
        try (EvaluationScopeContext ignored = EvaluationContext.current().open(owner)) {
            if (mayHaveValue(left)) {
                if (leftProducer.isKnown()) {
                    leftProducer.visitGuardedDependencies(context);
                }
                return;
            }
            if (right != null && rightProducer.isKnown() && mayHaveValue(right)) {
                rightProducer.visitGuardedDependencies(context);
            }
        }
    }

    private static boolean mayHaveValue(ProviderInternal<?> provider) {
        return !provider.calculateExecutionTimeValue().isMissing();
    }

}
