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

import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

import java.util.List;

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
    }

    /**
     * Adapter for property upgrade of {@link JavaForkOptions#getJvmArgs()}.
     */
    static class JvmArgsAdapter {

        @BytecodeUpgrade
        static List<String> getJvmArgs(JavaForkOptions options) {
            return options.getJvmArgs().get();
        }


    }
}
