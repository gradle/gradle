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

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

class OrElseValueProducer implements ValueSupplier.ValueProducer {

    private final ProviderInternal<?> left;
    private final ValueSupplier.ValueProducer leftProducer;
    private final ValueSupplier.ValueProducer rightProducer;

    public OrElseValueProducer(ProviderInternal<?> left, ValueSupplier.ValueProducer rightProducer) {
        this.left = left;
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

        List<Task> leftTasks = producerTasksOf(leftProducer);
        List<Task> rightTasks = producerTasksOf(rightProducer);

        if (rightTasks.isEmpty()) {
            if (leftTasks.isEmpty()) {
                return;
            }
            // We should only execute leftTasks if the left provider can ever produce a value
            if (!isLeftMissing()) {
                leftTasks.forEach(visitor::execute);
            }
            return;
        }

        if (leftTasks.isEmpty()) {
            return;
        }

        // TODO: configuration cache: this condition needs to be evaluated at execution time
        //  when `leftProducer.isProducesDifferentValueOverTime()`
        List<Task> producerTasks = isLeftMissing()
            ? rightTasks
            : leftTasks;
        producerTasks.forEach(visitor::execute);
    }

    private boolean isLeftMissing() {
        return left.calculateExecutionTimeValue().isMissing();
    }

    private List<Task> producerTasksOf(ValueSupplier.ValueProducer producer) {
        if (!producer.isKnown()) {
            return emptyList();
        }
        ArrayList<Task> tasks = new ArrayList<>();
        producer.visitProducerTasks(tasks::add);
        return tasks;
    }
}
