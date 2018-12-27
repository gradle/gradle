/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile;

import com.google.common.collect.Maps;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Base class for compilation-related options.
 */
public abstract class AbstractOptions implements Serializable {
    private static final long serialVersionUID = 0;

    public void define(@Nullable Map<String, Object> args) {
        if (args == null) {
            return;
        }
        for (Map.Entry<String, Object> arg: args.entrySet()) {
            setProperty(arg.getKey(), arg.getValue());
        }
    }

    public Map<String, Object> optionMap() {
        final Map<String, Object> map = Maps.newHashMap();
        Class<?> currClass = new DslObject(this).getDeclaredType();
        while (currClass != AbstractOptions.class) {
            for (final Field field : currClass.getDeclaredFields()) {
                if (isOptionField(field)) {
                    DeprecationLogger.whileDisabled(new Runnable() {
                        @Override
                        public void run() {
                            addValueToMapIfNotNull(map, field);
                        }
                    });
                }
            }
            currClass = currClass.getSuperclass();
        }
        return map;
    }

    protected boolean excludeFromAntProperties(String fieldName) {
        return false;
    }

    protected String getAntPropertyName(String fieldName) {
        return fieldName;
    }

    protected Object getAntPropertyValue(String fieldName, Object value) {
        return value;
    }

    private void setProperty(String property, Object value) {
        JavaReflectionUtil.writeableProperty(getClass(), property, value == null ? null : value.getClass()).setValue(this, value);
    }

    private void addValueToMapIfNotNull(Map<String, Object> map, Field field) {
        Object value = JavaReflectionUtil.readableProperty(this, Object.class, field.getName()).getValue(this);
        if (value != null) {
            map.put(getAntPropertyName(field.getName()), getAntPropertyValue(field.getName(), value));
        }
    }

    private boolean isOptionField(Field field) {
        return ((field.getModifiers() & Modifier.STATIC) == 0)
                && (!field.getName().equals("metaClass"))
                && (!field.getName().equals("fileResolver"))
                && (!excludeFromAntProperties(field.getName()));
    }
}
