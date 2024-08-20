/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.isolated.models;

public class IsolatedModelAccessKey<T> {

    private final IsolatedModelScope producer;
    private final IsolatedModelKey<T> key;
    private final IsolatedModelScope consumer;

    public IsolatedModelAccessKey(
        IsolatedModelScope producer,
        IsolatedModelKey<T> key,
        IsolatedModelScope consumer
    ) {
        this.producer = producer;
        this.key = key;
        this.consumer = consumer;
    }

    public IsolatedModelScope getProducer() {
        return producer;
    }

    public IsolatedModelKey<T> getModelKey() {
        return key;
    }

    public IsolatedModelScope getConsumer() {
        return consumer;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IsolatedModelAccessKey)) {
            return false;
        }

        IsolatedModelAccessKey<?> that = (IsolatedModelAccessKey<?>) o;
        return producer.equals(that.producer) && key.equals(that.key) && consumer.equals(that.consumer);
    }

    @Override
    public int hashCode() {
        int result = producer.hashCode();
        result = 31 * result + key.hashCode();
        result = 31 * result + consumer.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "IsolatedModelAccessKey{" +
            "producer=" + producer +
            ", key=" + key +
            ", consumer=" + consumer +
            '}';
    }
}
