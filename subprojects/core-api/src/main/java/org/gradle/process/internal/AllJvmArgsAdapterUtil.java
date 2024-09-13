/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.api.NonNullApi;
import org.gradle.process.JavaDebugOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for AllJvmArgsAdapter. It should be removed once AllJvmArgsAdapter is removed
 * and code should be moved to JvmOptions as it was original.
 */
@NonNullApi
public class AllJvmArgsAdapterUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllJvmArgsAdapterUtil.class);

    public static void checkDebugConfiguration(JavaDebugOptions debugOptions, Iterable<?> arguments) {
        List<String> debugArgs = collectDebugArgs(arguments);
        if (!debugArgs.isEmpty() && debugOptions.getEnabled().get()) {
            LOGGER.warn("Debug configuration ignored in favor of the supplied JVM arguments: {}", debugArgs);
            debugOptions.getEnabled().set(false);
        }
    }

    public static List<String> collectDebugArgs(Iterable<?> arguments) {
        List<String> debugArgs = new ArrayList<>();
        for (Object extraJvmArg : arguments) {
            String extraJvmArgString = extraJvmArg.toString();
            if (isDebugArg(extraJvmArgString)) {
                debugArgs.add(extraJvmArgString);
            }
        }
        return debugArgs;
    }

    private static boolean isDebugArg(String extraJvmArgString) {
        return extraJvmArgString.equals("-Xdebug")
            || extraJvmArgString.startsWith("-Xrunjdwp")
            || extraJvmArgString.startsWith("-agentlib:jdwp");
    }
}
