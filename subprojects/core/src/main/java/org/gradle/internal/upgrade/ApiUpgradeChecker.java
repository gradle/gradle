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

package org.gradle.internal.upgrade;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import groovy.json.JsonSlurper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.stream;

public class ApiUpgradeChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiUpgradeChecker.class);

    private final ImmutableList<Method> members;
    private static final Pattern METHOD_PATTERN = Pattern.compile("Method (\\w+(?:\\.\\w+)*) (\\w+(?:\\.\\w+)*)\\.(\\w+)\\((.*)\\)");
    private static final Pattern COMMA_LIST_PATTERN = Pattern.compile(",\\s*");

    public ApiUpgradeChecker() {
        String apiChangesPath = System.getProperty("org.gradle.upgrade.check");
        this.members = apiChangesPath == null ? ImmutableList.of() : parseAcceptedApiChanges(apiChangesPath);
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    static ImmutableList<Method> parseAcceptedApiChanges(String apiChangesPath) {
        Map<String, Object> apiChangesJson = (Map<String, Object>) new JsonSlurper().parse(new File(apiChangesPath));
        List<Object> acceptedApIChanges = (List<Object>) apiChangesJson.get("acceptedApiChanges");
        return acceptedApIChanges.stream().map(acceptedApiChangeJson -> {
                Map<String, Object> acceptedApiChange = (Map<String, Object>) acceptedApiChangeJson;
                String member = (String) acceptedApiChange.get("member");
                Matcher methodMatcher = METHOD_PATTERN.matcher(member);
                if (methodMatcher.matches()) {
                    String returnTypeName = methodMatcher.group(1);
                    String typeName = methodMatcher.group(2);
                    String methodName = methodMatcher.group(3);
                    String[] parameterTypeNames = COMMA_LIST_PATTERN.split(methodMatcher.group(4));
                    Class<?> type;
                    try {
                        type = Class.forName(typeName);
                    } catch (ClassNotFoundException e) {
                        LOGGER.error("Cannot find upgraded type {} for {}", typeName, member, e);
                        return null;
                    }
                    Class<?>[] parameterTypes = stream(parameterTypeNames)
                        .map(parameterTypeName -> {
                            try {
                                return Class.forName(parameterTypeName);
                            } catch (ClassNotFoundException e) {
                                LOGGER.error("Cannot find upgraded parameter type {} for {}", typeName, member, e);
                                return null;
                            }
                        })
                        .toArray(Class[]::new);
                    if (stream(parameterTypes).anyMatch(Objects::isNull)) {
                        return null;
                    }

                    try {
                        return type.getMethod(methodName, parameterTypes);
                    } catch (NoSuchMethodException e) {
                        LOGGER.error("Cannot find upgraded {}", member, e);
                        return null;
                    }
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(ImmutableList.toImmutableList());
    }
}
