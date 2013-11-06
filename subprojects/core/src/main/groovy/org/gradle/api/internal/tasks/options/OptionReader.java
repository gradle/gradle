/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.options;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.Task;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class OptionReader {

    private ListMultimap<Class, StaticOptionDescriptor> cachedClassDescriptors = ArrayListMultimap.create();

    public List<OptionDescriptor> getOptions(Task task) {
        final Class<? extends Task> taskClazz = task.getClass();
        Map<String, OptionDescriptor> options = new HashMap<String, OptionDescriptor>();
        if (!cachedClassDescriptors.containsKey(taskClazz)) {
            loadClassDescriptorInCache(task);
        }
        for (StaticOptionDescriptor staticCommandLineOptionDescriptor : cachedClassDescriptors.get(taskClazz)) {
            options.put(staticCommandLineOptionDescriptor.getName(), new InstanceOptionDescriptor(task, staticCommandLineOptionDescriptor));
        }
        return CollectionUtils.sort(options.values());
    }

    private void loadClassDescriptorInCache(Task task) {
        final Collection<StaticOptionDescriptor> staticDescriptors = getStaticDescriptors(task);
        Set<String> processedOptionDescriptors = new HashSet<String>();
        for (StaticOptionDescriptor staticDescriptor : staticDescriptors) {
            if (processedOptionDescriptors.contains(staticDescriptor.getName())) {
                throw new OptionValidationException(String.format("Option '%s' linked to multiple elements in class '%s'.",
                        staticDescriptor.getName(), task.getClass().getName()));
            }
            processedOptionDescriptors.add(staticDescriptor.getName());
            cachedClassDescriptors.put(task.getClass(), staticDescriptor);
        }
    }

    private Collection<StaticOptionDescriptor> getStaticDescriptors(Task task) {
        List<StaticOptionDescriptor> staticDescriptors = new ArrayList<StaticOptionDescriptor>();
        for (Class<?> type = task.getClass(); type != Object.class && type != null; type = type.getSuperclass()) {
            staticDescriptors.addAll(getMethodAnnotations(type));
            staticDescriptors.addAll(getFieldAnnotations(type));
        }
        return staticDescriptors;
    }

    private List<StaticOptionDescriptor> getFieldAnnotations(Class<?> type) {
        List<StaticOptionDescriptor> staticDescriptors = new ArrayList<StaticOptionDescriptor>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                Option option = field.getAnnotation(Option.class);
                if (option != null) {
                    String optionName;
                    if (option.options()[0].length() == 0) {
                        optionName = field.getName();
                    } else {
                        optionName = option.options()[0];
                    }
                    staticDescriptors.add(new StaticOptionDescriptor(optionName, option, new FieldOptionElement(optionName, field)));
                }
            }
        }
        return staticDescriptors;
    }

    private List<StaticOptionDescriptor> getMethodAnnotations(Class<?> type) {
        List<StaticOptionDescriptor> staticDescriptors = new ArrayList<StaticOptionDescriptor>();
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                Option option = method.getAnnotation(Option.class);
                if (option != null) {
                    final String optionName = option.options()[0];
                    final StaticOptionDescriptor methodOptionDescriptor = new StaticOptionDescriptor(optionName, option, new MethodOptionElement(optionName, method));
                    staticDescriptors.add(methodOptionDescriptor);
                }
            }
        }
        return staticDescriptors;
    }
}
