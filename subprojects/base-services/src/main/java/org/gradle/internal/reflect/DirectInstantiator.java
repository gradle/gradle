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
package org.gradle.internal.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DirectInstantiator implements Instantiator {
    public <T> T newInstance(Class<? extends T> type, Object... params) {
        try {
            List<Constructor<?>> matches = new ArrayList<Constructor<?>>();
            for (Constructor<?> constructor : type.getConstructors()) {
                if (isMatch(constructor, params)) {
                    matches.add(constructor);
                }
            }
            if (matches.isEmpty()) {
                throw new IllegalArgumentException(String.format("Could not find any public constructor for %s which accepts parameters %s.", type, Arrays.toString(params)));
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException(String.format("Found multiple public constructors for %s which accept parameters %s.", type, Arrays.toString(params)));
            }
            return type.cast(matches.get(0).newInstance(params));
        } catch (InvocationTargetException e) {
            throw new ObjectInstantiationException(type, e.getCause());
        } catch (Exception e) {
            throw new ObjectInstantiationException(type, e);
        }
    }

    private boolean isMatch(Constructor<?> constructor, Object... params) {
        if (constructor.getParameterTypes().length != params.length) {
            return false;
        }
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            Class<?> parameterType = constructor.getParameterTypes()[i];
            if (parameterType.isPrimitive()) {
                if (!JavaReflectionUtil.getWrapperTypeForPrimitiveType(parameterType).isInstance(param)) {
                    return false;
                }
            } else {
                if (param != null && !parameterType.isInstance(param)) {
                    return false;
                }
            }
        }
        return true;
    }
}
