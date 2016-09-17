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
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OptionReader {
    private final ListMultimap<Class<?>, OptionElement> cachedOptionElements = ArrayListMultimap.create();
    private final Map<OptionElement, JavaMethod<Object, Collection>> cachedOptionValueMethods = new HashMap<OptionElement, JavaMethod<Object, Collection>>();
    private final OptionValueNotationParserFactory optionValueNotationParserFactory = new OptionValueNotationParserFactory();

    public List<OptionDescriptor> getOptions(Object target) {
        final Class<?> targetClass = target.getClass();
        Map<String, OptionDescriptor> options = new HashMap<String, OptionDescriptor>();
        if (!cachedOptionElements.containsKey(targetClass)) {
            loadClassDescriptorInCache(target);
        }
        for (OptionElement optionElement : cachedOptionElements.get(targetClass)) {
            JavaMethod<Object, Collection> optionValueMethod = cachedOptionValueMethods.get(optionElement);
            options.put(optionElement.getOptionName(), new InstanceOptionDescriptor(target, optionElement, optionValueMethod));
        }
        return CollectionUtils.sort(options.values());
    }

    private void loadClassDescriptorInCache(Object target) {
        final Collection<OptionElement> optionElements = getOptionElements(target);
        List<JavaMethod<Object, Collection>> optionValueMethods = loadValueMethodForOption(target.getClass());
        Set<String> processedOptionElements = new HashSet<String>();
        for (OptionElement optionElement : optionElements) {
            if (processedOptionElements.contains(optionElement.getOptionName())) {
                throw new OptionValidationException(String.format("@Option '%s' linked to multiple elements in class '%s'.",
                        optionElement.getOptionName(), target.getClass().getName()));
            }
            processedOptionElements.add(optionElement.getOptionName());
            JavaMethod<Object, Collection> optionValueMethodForOption = getOptionValueMethodForOption(optionValueMethods, optionElement);

            cachedOptionElements.put(target.getClass(), optionElement);
            cachedOptionValueMethods.put(optionElement, optionValueMethodForOption);
        }
    }

    private static JavaMethod<Object, Collection> getOptionValueMethodForOption(List<JavaMethod<Object, Collection>> optionValueMethods, OptionElement optionElement) {
        JavaMethod<Object, Collection> valueMethod = null;
        for (JavaMethod<Object, Collection> optionValueMethod : optionValueMethods) {
            OptionValues optionValues = optionValueMethod.getMethod().getAnnotation(OptionValues.class);
            if (CollectionUtils.toList(optionValues.value()).contains(optionElement.getOptionName())) {
                            if (valueMethod == null) {
                                valueMethod = optionValueMethod;
                            } else {
                                throw new OptionValidationException(
                                        String.format("@OptionValues for '%s' cannot be attached to multiple methods in class '%s'.",
                                                optionElement.getOptionName(),
                                                optionValueMethod.getMethod().getDeclaringClass().getName()));
                            }
                        }
        }
        return valueMethod;
    }

    private Collection<OptionElement> getOptionElements(Object target) {
        List<OptionElement> allOptionElements = new ArrayList<OptionElement>();
        for (Class<?> type = target.getClass(); type != Object.class && type != null; type = type.getSuperclass()) {
            allOptionElements.addAll(getMethodAnnotations(type));
            allOptionElements.addAll(getFieldAnnotations(type));
        }

        return allOptionElements;
    }

    private List<OptionElement> getFieldAnnotations(Class<?> type) {
        List<OptionElement> fieldOptionElements = new ArrayList<OptionElement>();
        for (Field field : type.getDeclaredFields()) {
            Option option = field.getAnnotation(Option.class);
            if (option != null) {
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new OptionValidationException(String.format("@Option on static field '%s' not supported in class '%s'.",
                            field.getName(), field.getDeclaringClass().getName()));
                }

                fieldOptionElements.add(FieldOptionElement.create(option, field, optionValueNotationParserFactory));
            }
        }
        return fieldOptionElements;
    }

    private List<OptionElement> getMethodAnnotations(Class<?> type) {
        List<OptionElement> methodOptionElements = new ArrayList<OptionElement>();
        for (Method method : type.getDeclaredMethods()) {
            Option option = method.getAnnotation(Option.class);
            if (option != null) {
                if (Modifier.isStatic(method.getModifiers())) {
                    throw new OptionValidationException(String.format("@Option on static method '%s' not supported in class '%s'.",
                            method.getName(), method.getDeclaringClass().getName()));
                }
                final OptionElement methodOptionDescriptor = MethodOptionElement.create(option, method,
                    optionValueNotationParserFactory);
                methodOptionElements.add(methodOptionDescriptor);
            }
        }
        return methodOptionElements;
    }

    private static List<JavaMethod<Object, Collection>> loadValueMethodForOption(Class<?> declaredClass) {
        List<JavaMethod<Object, Collection>> methods = new ArrayList<JavaMethod<Object, Collection>>();
        for (Class<?> type = declaredClass; type != Object.class && type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                OptionValues optionValues = method.getAnnotation(OptionValues.class);
                if (optionValues != null) {
                    if (Collection.class.isAssignableFrom(method.getReturnType())
                            && method.getParameterTypes().length == 0
                            && !Modifier.isStatic(method.getModifiers())) {

                        methods.add(JavaReflectionUtil.method(Collection.class, method));
                    } else {
                        throw new OptionValidationException(
                                String.format("@OptionValues annotation not supported on method '%s' in class '%s'. Supported method must be non-static, return a Collection<String> and take no parameters.",
                                        method.getName(),
                                        type.getName()));
                    }
                }
            }
        }
        return methods;
    }

}
