/*
 * Copyright 2017 the original author or authors.
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

import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.MutableActionSet;

import java.io.IOException;
import java.io.OutputStream;

public class PropertyListTransformer<T extends NSObject> implements Transformer<T, T> {
    private final MutableActionSet<T> actions = new MutableActionSet<T>();

    /**
     * Adds an action to be executed when property lists are transformed.
     * @param action the action to add
     */
    public void addAction(Action<? super T> action) {
        actions.add(action);
    }

    /**
     * Transforms a property list object. This will modify the
     * original.
     * @param original the property list to transform
     * @return the transformed property list
     */
    @Override
    public T transform(T original) {
        return doTransform(original);
    }

    /**
     * Transforms a property list object and write them out to a stream.
     * This will modify the original property list.
     * @param original the property list to transform
     * @param destination the stream to write the property list to
     */
    public void transform(T original, OutputStream destination) {
        try {
            PropertyListParser.saveAsXML(doTransform(original), destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Transforms a property list object.  This will modify the
     * original.
     * @param original the property list to transform
     * @return the transformed property list
     */
    private T doTransform(T original) {
        actions.execute(original);
        return original;
    }
}
