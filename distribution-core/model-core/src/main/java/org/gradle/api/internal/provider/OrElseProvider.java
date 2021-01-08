/*
 * Copyright 2020 the original author or authors.
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
import java.util.ArrayList;

class OrElseProvider<T> extends AbstractMinimalProvider<T> {
    private final ProviderInternal<T> left;
    private final ProviderInternal<? extends T> right;

    public OrElseProvider(ProviderInternal<T> left, ProviderInternal<? extends T> right) {
        this.left = left;
        this.right = right;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return left.getType();
    }

    @Override
    public ValueProducer getProducer() {
        ExecutionTimeValue<? extends T> leftValue = left.calculateExecutionTimeValue();
        return leftValue.isMissing()
            ? right.getProducer()
            : new OrElseValueProducer();
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return left.calculatePresence(consumer) || right.calculatePresence(consumer);
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        ExecutionTimeValue<? extends T> leftValue = left.calculateExecutionTimeValue();
        if (!leftValue.isMissing()) {
            // favour left execution time value if present for better configuration cache integration
            // of idioms like `property.convention(provider.orElse(somethingElse))`
            return leftValue;
        }
        return super.calculateExecutionTimeValue();
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        Value<? extends T> leftValue = left.calculateValue(consumer);
        if (!leftValue.isMissing()) {
            return leftValue;
        }
        Value<? extends T> rightValue = right.calculateValue(consumer);
        if (!rightValue.isMissing()) {
            return rightValue;
        }
        return leftValue.addPathsFrom(rightValue);
    }

    private class OrElseValueProducer implements ValueProducer {

        final ValueProducer leftProducer = left.getProducer();
        final ValueProducer rightProducer = right.getProducer();

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
            ArrayList<Task> leftTasks = producerTasksOf(leftProducer);
            ArrayList<Task> rightTasks = producerTasksOf(rightProducer);
            if (leftTasks.isEmpty() && rightTasks.isEmpty()) {
                return;
            }
            // TODO: configuration cache: this condition needs to be evaluated at execution time
            //  when `leftProducer.isProducesDifferentValueOverTime()`
            ArrayList<Task> producerTasks = left.isPresent()
                ? leftTasks
                : rightTasks;
            producerTasks.forEach(visitor::execute);
        }

        private ArrayList<Task> producerTasksOf(ValueProducer producer) {
            ArrayList<Task> tasks = new ArrayList<>();
            producer.visitProducerTasks(tasks::add);
            return tasks;
        }

    }
}
