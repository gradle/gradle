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

package org.gradle.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class RuleHelper {
    public static <T> T getField(Object target, Class<T> type) {
        T value = findField(target, type);
        if (value != null) {
            return value;
        }
        throw new RuntimeException(String.format("Cannot find a field of type %s for test class %s.",
                type.getSimpleName(), target.getClass().getSimpleName()));
    }

    public static <T> T findField(Object target, Class<T> type) {
        List<T> matches = new ArrayList<T>();
        for (Class<?> cl = target.getClass(); cl != Object.class; cl = cl.getSuperclass()) {
            for (Field field : cl.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        matches.add(type.cast(field.get(target)));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new RuntimeException(String.format("Multiple %s fields found for test class %s.",
                    type.getSimpleName(), target.getClass().getSimpleName()));
        }
        return matches.get(0);
    }
}
