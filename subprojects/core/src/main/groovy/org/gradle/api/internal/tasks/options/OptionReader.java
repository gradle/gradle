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

    private ListMultimap<Class, OptionElement> cachedStaticClassDescriptors = ArrayListMultimap.create();

    public List<OptionDescriptor> getOptions(Task task) {
        final Class<? extends Task> taskClazz = task.getClass();
        Map<String, OptionDescriptor> options = new HashMap<String, OptionDescriptor>();
        if (!cachedStaticClassDescriptors.containsKey(taskClazz)) {
            loadClassDescriptorInCache(task);
        }
        for (OptionElement optionElement : cachedStaticClassDescriptors.get(taskClazz)) {
            options.put(optionElement.getOptionName(), new InstanceOptionDescriptor(task, optionElement));
        }
        return CollectionUtils.sort(options.values());
    }

    private void loadClassDescriptorInCache(Task task) {
        final Collection<OptionElement> optionElements = getOptionElements(task);
        Set<String> processedOptionElements = new HashSet<String>();
        for (OptionElement optionElement : optionElements) {
            if (processedOptionElements.contains(optionElement.getOptionName())) {
                throw new OptionValidationException(String.format("Option '%s' linked to multiple elements in class '%s'.",
                        optionElement.getOptionName(), task.getClass().getName()));
            }
            processedOptionElements.add(optionElement.getOptionName());
            cachedStaticClassDescriptors.put(task.getClass(), optionElement);
        }
    }

    private Collection<OptionElement> getOptionElements(Task task) {
        List<OptionElement> allOptionElements = new ArrayList<OptionElement>();
        for (Class<?> type = task.getClass(); type != Object.class && type != null; type = type.getSuperclass()) {
            allOptionElements.addAll(getMethodAnnotations(type));
            allOptionElements.addAll(getFieldAnnotations(type));
        }
        return allOptionElements;
    }

    private List<OptionElement> getFieldAnnotations(Class<?> type) {
        List<OptionElement> fieldOptionElements = new ArrayList<OptionElement>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                Option option = field.getAnnotation(Option.class);
                if (option != null) {

                    fieldOptionElements.add(new FieldOptionElement(option, field));
                }
            }
        }
        return fieldOptionElements;
    }

    private List<OptionElement> getMethodAnnotations(Class<?> type) {
        List<OptionElement> methodOptionElements = new ArrayList<OptionElement>();
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                Option option = method.getAnnotation(Option.class);
                if (option != null) {
                    final OptionElement methodOptionDescriptor = new MethodOptionElement(option, method);
                    methodOptionElements.add(methodOptionDescriptor);
                }
            }
        }
        return methodOptionElements;
    }
}
