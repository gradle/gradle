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
import org.gradle.api.Nullable;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.DeprecationLogger;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Base class for compilation-related options.
 *
 * @author Hans Dockter
 */
public abstract class AbstractOptions implements Serializable {
    private static final long serialVersionUID = 0;

    public void define(@Nullable Map<String, Object> args) {
        if (args == null) { return; }
        for (Map.Entry<String, Object> arg: args.entrySet()) {
            JavaReflectionUtil.writeProperty(this, arg.getKey(), arg.getValue());
        }
    }

    public Map<String, Object> optionMap() {
        final Class<? extends AbstractOptions> thisClass = getClass();
        return DeprecationLogger.whileDisabled(new Factory<Map<String, Object>>() {
            public Map<String, Object> create() {
                Map<String, Object> map = Maps.newHashMap();
                for (Field field : thisClass.getDeclaredFields()) {
                    if (!isOptionField(field)) {
                        continue;
                    }
                    addValueToMapIfNotNull(map, field);
                }
                return map;
            }
        });
    }

    private void addValueToMapIfNotNull(Map<String, Object> map, Field field) {
        Object value = JavaReflectionUtil.readProperty(this, field.getName());
        if (value != null) {
            map.put(antProperty(field.getName()), antValue(field.getName(), value));
        }
    }

    private boolean isOptionField(Field field) {
        return ((field.getModifiers() & Modifier.STATIC) == 0)
                && (!field.getName().equals("metaClass"))
                && (!excludedFieldsFromOptionMap().contains(field.getName()));
    }

    private String antProperty(String fieldName) {
        if (fieldName2AntMap().containsKey(fieldName)) {
            return fieldName2AntMap().get(fieldName);
        }
        return fieldName;
    }

    private Object antValue(String fieldName, Object value) {
        if (fieldValue2AntMap().containsKey(fieldName)) {
            try {
                return fieldValue2AntMap().get(fieldName).call();
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return value;
    }

    protected List<String> excludedFieldsFromOptionMap() {
        return Collections.emptyList();
    }

    protected Map<String, String> fieldName2AntMap() {
        return Collections.emptyMap();
    }

    protected Map<String, ? extends Callable<Object>> fieldValue2AntMap() {
        return Collections.emptyMap();
    }
}
