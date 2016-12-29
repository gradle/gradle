/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.NotSerializableException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

import static java.lang.String.format;

public final class InputProperties {
    private InputProperties() {}

    public static Map<String, InputProperty> process(final Map<String, Object> inputProperties) {
        return CollectionUtils.collectMapValues(inputProperties.keySet(), new Transformer<InputProperty, String>() {
            @Override
            public InputProperty transform(String key) {
                Object value = inputProperties.get(key);
                try {
                    return create(value);
                } catch (NotSerializableException e) {
                    throw new GradleException(format("Unable to hash task input properties. Property '%s' with value '%s' cannot be serialized.", key, value), e);
                }
            }
        });
    }

    public static InputProperty create(Object inputProperty) throws NotSerializableException {
        if (isBinaryComparableProperty(inputProperty)) {
            return BinaryInputProperty.create(inputProperty);
        } else {
            return DefaultInputProperty.create(inputProperty);
        }
    }

    private static boolean isBinaryComparableProperty(Object inputProperty) {
        if (inputProperty == null) {
            return false;
        }

        Deque<Object> processingQueue = new ArrayDeque<Object>();
        processingQueue.add(inputProperty);
        while (!processingQueue.isEmpty()) {
            Object item = processingQueue.pop();

            Class cls = item.getClass();
            if (SortedSet.class.isAssignableFrom(cls)) {
                SortedSet collection = (SortedSet) item;
                if (collection.isEmpty()) {
                    return false;
                }
                processingQueue.add(collection.iterator().next());
            } else if (SortedMap.class.isAssignableFrom(cls)) {
                SortedMap map = (SortedMap) inputProperty;
                if (map.isEmpty()) {
                    return false;
                }
                Map.Entry entry = (Map.Entry) map.entrySet().iterator().next();
                processingQueue.add(entry.getKey());
                processingQueue.add(entry.getValue());
            } else {
                if (!CharSequence.class.isAssignableFrom(cls)
                    && !Number.class.isAssignableFrom(cls)
                    && !File.class.isAssignableFrom(cls)
                    && !Boolean.class.isAssignableFrom(cls)) {
                    return false;
                }
            }
        }

        return true;
    }
}
