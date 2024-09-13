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

package org.gradle.process;

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

import java.io.File;
import java.util.List;

import static org.gradle.process.internal.AllJvmArgsAdapterUtil.checkDebugConfiguration;

/**
 * Adapters for property upgrades of {@link JavaForkOptions}.
 */
class JavaForkOptionsAdapters {

    /**
     * Adapter for property upgrade of {@link JavaForkOptions#getAllJvmArgs()}.
     */
    static class AllJvmArgsAdapter {

        @BytecodeUpgrade
        static List<String> getAllJvmArgs(JavaForkOptions options) {
            return options.getAllJvmArgs().get();
        }

        @BytecodeUpgrade
        static void setAllJvmArgs(JavaForkOptions options, List<String> arguments) {
            unsetOptions(options);
            addExtraJvmArgs(options, arguments);
        }

        @BytecodeUpgrade
        static void setAllJvmArgs(JavaForkOptions options, Iterable<?> arguments) {
            unsetOptions(options);
            addExtraJvmArgs(options, arguments);
            checkDebugConfiguration(options.getDebugOptions(), arguments);
        }

        private static void unsetOptions(JavaForkOptions options) {
            options.getSystemProperties().empty();
            options.getJvmArgs().empty();
            options.getMinHeapSize().unset();
            options.getMaxHeapSize().unset();
            options.getMaxHeapSize().unset();
            options.getEnableAssertions().unset();
            options.getDebugOptions().getEnabled().unset();
            options.getDebugOptions().getPort().unset();
            options.getDebugOptions().getServer().unset();
            options.getDebugOptions().getSuspend().unset();
            options.getDebugOptions().getHost().unset();
        }

        /**
         * This logic is the same as JvmOptions#addExtraJvmArgs for backward compatibility
         */
        private static void addExtraJvmArgs(JavaForkOptions options, Iterable<?> arguments) {
            for (Object argument : arguments) {
                String argStr = argument.toString();
                if (argStr.equals("-ea") || argStr.equals("-enableassertions")) {
                    options.getEnableAssertions().set(true);
                } else if (argStr.equals("-da") || argStr.equals("-disableassertions")) {
                    options.getEnableAssertions().set(false);
                } else if (argStr.startsWith("-Xms")) {
                    options.getMinHeapSize().set(argStr.substring("-Xms".length()));
                } else if (argStr.startsWith("-Xmx")) {
                    options.getMaxHeapSize().set(argStr.substring("-Xmx".length()));
                } else if (argStr.startsWith("-Xbootclasspath:")) {
                    String[] bootClasspath = StringUtils.split(argStr.substring("-Xbootclasspath:".length()), File.pathSeparatorChar);
                    options.getBootstrapClasspath().setFrom((Object[]) bootClasspath);
                } else if (argStr.startsWith("-D")) {
                    String keyValue = argStr.substring(2);
                    int equalsIndex = keyValue.indexOf("=");
                    if (equalsIndex == -1) {
                        options.systemProperty(keyValue, "");
                    } else {
                        options.systemProperty(keyValue.substring(0, equalsIndex), keyValue.substring(equalsIndex + 1));
                    }
                } else {
                    options.jvmArgs(argument);
                }
            }
        }
    }

    /**
     * Adapter for property upgrade of {@link JavaForkOptions#getJvmArgs()}.
     */
    static class JvmArgsAdapter {

        @BytecodeUpgrade
        static List<String> getJvmArgs(JavaForkOptions options) {
            return options.getJvmArgs().get();
        }

        @BytecodeUpgrade
        static void setJvmArgs(JavaForkOptions options, List<String> arguments) {
            options.jvmArgs(arguments);
        }

        @BytecodeUpgrade
        static void setJvmArgs(JavaForkOptions options, Iterable<?> arguments) {
            options.jvmArgs(arguments);
        }
    }
}
