/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade.report;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import groovy.json.JsonSlurper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

class ApiUpgradeJsonParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiUpgradeJsonParser.class);

    private static final Pattern METHOD_PATTERN = Pattern.compile("Method (\\w+(?:\\.\\w+)*) (\\w+(?:\\.\\w+)*)\\.(\\w+)\\((.*)\\)");
    private static final Pattern COMMA_LIST_PATTERN = Pattern.compile(",\\s*");

    private static final Map<String, Class<?>> PRIMITIVE_CLASSES = Stream.<Class<?>>of(
        boolean.class,
        byte.class,
        short.class,
        int.class,
        long.class,
        float.class,
        double.class,
        char.class
    ).collect(toMap(Class::getName, identity()));

    private static final Map<String, List<String>> ALL_KNOWN_SUBTYPES = ImmutableMap.<String, List<String>>builder()
        .put("org.gradle.api.tasks.compile.AbstractCompile", ImmutableList.of("org.gradle.api.tasks.compile.JavaCompile",
            "org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile",
            "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
            "org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask",
            "org.gradle.api.tasks.compile.GroovyCompile")
        )
        .build();

    public ImmutableList<ReportableApiChange> parseAcceptedApiChanges(File apiChangesPath) {
        List<JsonApiChange> jsonApiChanges = parseApiChangeFile(apiChangesPath);
        return jsonApiChanges.stream()
            .map(this::mapToApiChange)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(ImmutableList.toImmutableList());
    }

    private @Nonnull Optional<ReportableApiChange> mapToApiChange(JsonApiChange jsonApiChange) {
        String member = jsonApiChange.member;
        Matcher methodMatcher = METHOD_PATTERN.matcher(member);
        if (!methodMatcher.matches()) {
            return Optional.empty();
        }

        String typeName = methodMatcher.group(2);
        String methodName = methodMatcher.group(3);
        List<String> parameterTypeNames = Arrays.stream(COMMA_LIST_PATTERN.split(methodMatcher.group(4)))
            .filter(split -> !split.isEmpty())
            .collect(Collectors.toList());
        Optional<Class<?>> type = getClassForName(typeName);
        if (!type.isPresent()) {
            // This means type is not on a classpath, so it doesn't need upgrade
            return Optional.empty();
        }

        List<Class<?>> parameterTypes = parameterTypeNames.stream()
            .map(name -> getClassForName(name).orElse(null))
            .collect(Collectors.toList());
        if (parameterTypes.stream().anyMatch(Objects::isNull)) {
            LOGGER.error("Cannot find all type parameters {} for upgrade {}", parameterTypeNames, member);
            return Optional.empty();
        }

        try {
            Method method = type.get().getMethod(methodName, parameterTypes.toArray(new Class[0]));
            List<String> allKnownSubtypes = ALL_KNOWN_SUBTYPES.getOrDefault(type.get().getName(), Collections.emptyList());
            return Optional.of(new MethodReportableApiChange(jsonApiChange.type, allKnownSubtypes, method));
        } catch (NoSuchMethodException e) {
            // This means that method on classpath has different signature, so we cannot upgrade it
            LOGGER.error("Cannot find method for upgrade {}", member);
            return Optional.empty();
        }
    }

    private Optional<Class<?>> getClassForName(String name) {
        try {
            if (name.endsWith("[]")) {
                // TODO handle primitives also here
                String arrayName = "[L" + name.replace("[]", "") + ";";
                return Optional.of(Class.forName(arrayName));
            }
            Class<?> primitiveClass = PRIMITIVE_CLASSES.get(name);
            if (primitiveClass != null) {
                return Optional.of(primitiveClass);
            }
            return Optional.of(Class.forName(name));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<JsonApiChange> parseApiChangeFile(File apiChangesFile) {
        Map<String, Object> apiChangesJson = (Map<String, Object>) new JsonSlurper().parse(apiChangesFile);
        List<Map<String, Object>> acceptedApiChanges = (List<Map<String, Object>>) apiChangesJson.get("acceptedApiChanges");
        return acceptedApiChanges.stream()
            .map(this::mapToApiChange)
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private JsonApiChange mapToApiChange(Map<String, Object> change) {
        String type = (String) change.get("type");
        String member = (String) change.get("member");
        String acceptation = (String) change.get("acceptation");
        List<String> changes = (List<String>) change.get("changes");
        return new JsonApiChange(type, member, acceptation, changes);
    }

    private static class JsonApiChange {
        private final String type;
        private final String member;
        private final String acceptation;
        private final List<String> changes;

        private JsonApiChange(String type, String member, String acceptation, List<String> changes) {
            this.type = type;
            this.member = member;
            this.acceptation = acceptation;
            this.changes = changes;
        }
    }
}
