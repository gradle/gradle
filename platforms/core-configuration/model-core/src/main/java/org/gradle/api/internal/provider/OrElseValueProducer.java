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

import org.gradle.api.Action;
import org.gradle.api.Task;

import javax.annotation.Nullable;

class OrElseValueProducer implements ValueSupplier.ValueProducer {

    private final ProviderInternal<?> left;
    @Nullable
    private final ProviderInternal<?> right;
    private final ValueSupplier.ValueProducer leftProducer;
    private final ValueSupplier.ValueProducer rightProducer;

    public OrElseValueProducer(ProviderInternal<?> left, @Nullable ProviderInternal<?> right, ValueSupplier.ValueProducer rightProducer) {
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
    public boolean isProducesDifferentValueOverTime() {
        return leftProducer.isProducesDifferentValueOverTime()
            || rightProducer.isProducesDifferentValueOverTime();
    }

    @Override
    public void visitProducerTasks(Action<? super Task> visitor) {
        if (!isMissing(left)) {
            if (leftProducer.isKnown()) {
                leftProducer.visitProducerTasks(visitor);
            }
            return;
        }
        if (right != null && rightProducer.isKnown() && !isMissing(right)) {
            rightProducer.visitProducerTasks(visitor);
        }
    }

    private boolean isMissing(ProviderInternal<?> provider) {
        return provider.calculateExecutionTimeValue().isMissing();
    }
}
