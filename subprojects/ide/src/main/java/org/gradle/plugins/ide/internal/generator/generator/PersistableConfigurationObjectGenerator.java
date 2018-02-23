/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.internal.generator.generator;

import org.gradle.internal.Factory;

import java.io.File;

/**
 * Adapts a {@link PersistableConfigurationObject} to a {@link
 * Generator}.
 *
 * @param <T> the configuration object type.
 */
public abstract class PersistableConfigurationObjectGenerator<T extends PersistableConfigurationObject> implements Generator<T>, Factory<T> {
    @Override
    public T read(File inputFile) {
        T obj = create();
        obj.load(inputFile);
        return obj;
    }

    @Override
    public T defaultInstance() {
        T obj = create();
        obj.loadDefaults();
        return obj;
    }

    @Override
    public void write(T object, File outputFile) {
        object.store(outputFile);
    }
}
