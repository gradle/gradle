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
package org.gradle.internal.logging.console;

import org.gradle.internal.nativeintegration.console.ConsoleMetaData;

public class ConsoleLayoutCalculator {
    private final ConsoleMetaData consoleMetaData;
    private int maximumAvailableLines = -1;

    /**
     * @param consoleMetaData used to get console dimensions
     */
    public ConsoleLayoutCalculator(ConsoleMetaData consoleMetaData) {
        this.consoleMetaData = consoleMetaData;
    }
    /**
     * Calculate number of Console lines to use for work-in-progress display.
     *
     * @param ideal number of Console lines
     * @return height of progress area.
     */
    public int calculateNumWorkersForConsoleDisplay(int ideal) {
        if (maximumAvailableLines == -1) {
            // Disallow work-in-progress to take up more than half of the console display
            // If the screen size is unknown, allow 4 lines
            int rows = consoleMetaData.getRows();
            maximumAvailableLines = rows == 0 ? 4 : rows / 2;
        }

        return Math.min(ideal, maximumAvailableLines);
    }
}
