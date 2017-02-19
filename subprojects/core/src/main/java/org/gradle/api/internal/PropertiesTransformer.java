/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.MutableActionSet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Transformer implementation to support modification of Properties objects.
 */
public class PropertiesTransformer implements Transformer<Properties, Properties> {
    private final MutableActionSet<Properties> actions = new MutableActionSet<Properties>();

    /**
     * Adds an action to be executed when properties are transformed.
     * @param action the action to add
     */
    public void addAction(Action<? super Properties> action) {
        actions.add(action);
    }

    /**
     * Transforms a properties object.  This will modify the
     * original.
     * @param original the properties to transform
     * @return the transformed properties
     */
    public Properties transform(Properties original) {
        return doTransform(original);
    }

    /**
     * Transforms a properties object and write them out to a stream.
     * This will modify the original properties.
     * @param original the properties to transform
     * @param destination the stream to write the properties to
     */
    public void transform(Properties original, OutputStream destination) {
        try {
            doTransform(original).store(destination, "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Transforms a properties object.  This will modify the
     * original.
     * @param original the properties to transform
     * @return the transformed properties
     */
    private Properties doTransform(Properties original) {
        actions.execute(original);
        return original;
    }
}
