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

package org.gradle.api.internal.provider

import org.gradle.api.Task

trait ProviderAssertions {
    void assertHasNoProducer(ProviderInternal<?> provider) {
        def producer = provider.producer
        assert !producer.known
        producer.visitProducerTasks { assert false }
        producer.visitProducerExtras { assert false }
        producer.visitContentProducerTasks { assert false }
    }

    void assertHasKnownProducer(ProviderInternal<?> provider) {
        def producer = provider.producer
        assert producer.known
        producer.visitProducerTasks { assert false }
        producer.visitProducerExtras { assert false }
        producer.visitContentProducerTasks { assert false }
    }

    void assertHasProducer(ProviderInternal<?> provider, Task task, Task... additional) {
        def expected = [task] + (additional as List)

        def producer = provider.producer
        assert producer.known
        def tasks = []
        def taskExtras = []
        producer.visitProducerTasks { tasks.add(it) }
        producer.visitProducerExtras { if (it instanceof ValueSupplier.ValueProducer.ValueProducerExtra.TaskExtra) { tasks.add(it.task) } }
        assert taskExtras == expected
        assert tasks == expected
        tasks.clear()
        producer.visitContentProducerTasks { tasks.add(it) }
        assert tasks == expected
    }
}
