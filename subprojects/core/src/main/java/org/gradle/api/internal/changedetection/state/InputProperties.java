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

import java.io.File;
import java.io.NotSerializableException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.lang.String.format;

public abstract class InputProperties {
    public static Map<String, InputProperty> process(Map<String, Object> inputProperties) {
        SortedMap<String, InputProperty> result = new TreeMap<String, InputProperty>(new HashMap<String, InputProperty>(inputProperties.size()));
        for (Map.Entry<String, Object> entry : inputProperties.entrySet()) {
            try {
                result.put(entry.getKey(), create(entry.getValue()));
            } catch (NotSerializableException e) {
                throw new GradleException(format("Unable to hash task input properties. Property '%s' with value '%s' cannot be serialized.", entry.getKey(), entry.getValue()), e);
            }
        }
        return result;
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
            if (Collection.class.isAssignableFrom(cls)) {
                Collection collection = (Collection) item;
                if (collection.isEmpty()) {
                    return false;
                }
                processingQueue.add(collection.iterator().next());
            } else if (Map.class.isAssignableFrom(cls)) {
                Map map = (Map) inputProperty;
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
