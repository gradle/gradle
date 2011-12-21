/*
 * Copyright 2009 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DeprecationLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeprecationLogger.class);
    private static final Set<String> METHODS = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> NAMED_PARAMETERS = Collections.synchronizedSet(new HashSet<String>());

    public static void reset() {
        METHODS.clear();
    }

    public static void nagUserOfReplacedMethod(String methodName, String replacement) {
        if (METHODS.add(methodName)) {
            LOGGER.warn(String.format(
                    "The %s method has been deprecated and will be removed in the next version of Gradle. Please use the %s method instead.",
                    methodName, replacement));
        }
    }

    public static void nagUserOfDiscontinuedMethod(String methodName) {
        if (METHODS.add(methodName)) {
            LOGGER.warn(String.format("The %s method has been deprecated and will be removed in the next version of Gradle.",
                    methodName));
        }
    }

    public static void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        if (NAMED_PARAMETERS.add(parameterName)) {
            LOGGER.warn(String.format(
                    "The %s named parameter has been deprecated and will be removed in the next version of Gradle. Please use the %s named parameter instead.",
                    parameterName, replacement));
        }
    }

    public static void nagUserWith(String message) {
        if (METHODS.add(message)) {
            LOGGER.warn(message);
        }
    }
}
