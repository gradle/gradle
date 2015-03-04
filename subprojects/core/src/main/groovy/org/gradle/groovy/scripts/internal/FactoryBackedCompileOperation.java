/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import org.gradle.groovy.scripts.Transformer;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Serializer;

public class FactoryBackedCompileOperation<T> implements CompileOperation<T> {

    private final String id;
    private final Transformer transformer;
    private final Factory<T> dataFactory;
    private final Serializer<T> serializer;

    public FactoryBackedCompileOperation(String id, Transformer transformer, Factory<T> dataFactory, Serializer<T> serializer) {
        this.id = id;
        this.transformer = transformer;
        this.dataFactory = dataFactory;
        this.serializer = serializer;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Transformer getTransformer() {
        return transformer;
    }

    @Override
    public T getExtractedData() {
        return dataFactory.create();
    }

    @Override
    public Serializer<T> getDataSerializer() {
        return serializer;
    }
}
