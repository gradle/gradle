/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.nativeintegration.console;

import javax.annotation.Nullable;

public class TestOverrideConsoleDetector implements ConsoleDetector {
    public static final String INTERACTIVE_TOGGLE = "org.gradle.internal.interactive";
    public static final String TEST_CONSOLE_PROPERTY = "org.gradle.internal.console.test-console";

    private final ConsoleDetector detector;

    public TestOverrideConsoleDetector(ConsoleDetector detector) {
        this.detector = detector;
    }

    @Nullable
    @Override
    public ConsoleMetaData getConsole() {
        String testConsole = System.getProperty(TEST_CONSOLE_PROPERTY);
        if (testConsole != null) {
            return TestConsoleMetadata.valueOf(testConsole);
        }
        return detector.getConsole();
    }

    @Override
    public boolean isConsoleInput() {
        if (Boolean.getBoolean(TestOverrideConsoleDetector.INTERACTIVE_TOGGLE)) {
            return true;
        }
        return detector.isConsoleInput();
    }
}
