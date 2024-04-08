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

package org.gradle.testing.fixture

import org.gradle.api.JavaVersion;

/**
 * Class for supporting multiple JVMs with different parameters in testing tests.
 */
class MultiJvmTestCompatibility {
    /**
     * Check that the console is correct for a test worker. On Java 22, the console exists but is not a terminal. On earlier versions, the console does not exist.
     */
    static final String CONSOLE_CHECK = JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_22)
        ? "assertFalse(System.console().isTerminal());"
        : "assertNull(System.console());"
}
