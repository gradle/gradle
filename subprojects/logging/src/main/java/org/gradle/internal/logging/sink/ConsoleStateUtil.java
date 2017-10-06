/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.sink;

public final class ConsoleStateUtil {

    public static final String INTERACTIVE_TOGGLE = "org.gradle.interactive";

    private ConsoleStateUtil() {
    }

    public static boolean isInteractive() {
        return isInteractiveConsoleAttached() || definesInteractiveSystemProperty();
    }

    /**
     * Checks if console is associated with JVM.
     */
    private static boolean isInteractiveConsoleAttached() {
        return System.console() != null;
    }

    /**
     * Interactive toggle used by integration testing.
     */
    private static boolean definesInteractiveSystemProperty() {
        return Boolean.getBoolean(INTERACTIVE_TOGGLE);
    }
}
