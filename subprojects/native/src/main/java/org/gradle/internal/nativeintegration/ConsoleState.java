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

package org.gradle.internal.nativeintegration;

import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.nativeintegration.services.NativeServices;

public class ConsoleState {
    public static final String INTERACTIVE_TOGGLE = "org.gradle.interactive";
    private final boolean interactive;

    public ConsoleState() {
        this.interactive = stdOutIsAttachedToTerminal() || definesInteractiveSystemProperty();
    }

    public boolean isInteractive() {
        return interactive;
    }

    /**
     * Assume that standard input is available if standard output can be used.
     */
    private boolean stdOutIsAttachedToTerminal() {
        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
        ConsoleMetaData consoleMetaData = consoleDetector.getConsole();
        return consoleMetaData != null && consoleMetaData.isStdOut();
    }

    /**
     * Interactive toggle used by integration testing.
     */
    private boolean definesInteractiveSystemProperty() {
        return Boolean.getBoolean(INTERACTIVE_TOGGLE);
    }
}
