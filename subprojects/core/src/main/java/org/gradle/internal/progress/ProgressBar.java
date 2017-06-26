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

package org.gradle.internal.progress;

public class ProgressBar implements ProgressFormatter {
    private final String progressBarPrefix;
    private int progressBarWidth;
    private final String progressBarSuffix;
    private char fillerChar;
    private int current;
    private final char incompleteChar;
    private int total;
    private String suffix;

    public ProgressBar(String progressBarPrefix, int progressBarWidth, String progressBarSuffix, char completeChar, char incompleteChar, String suffix, int total) {
        this.progressBarPrefix = progressBarPrefix;
        this.progressBarWidth = progressBarWidth;
        this.progressBarSuffix = progressBarSuffix;
        this.fillerChar = completeChar;
        this.incompleteChar = incompleteChar;
        this.total = total;
        this.suffix = suffix;
    }

    public String incrementAndGetProgress() {
        increment();
        return getProgress();
    }

    public void increment() {
        if (current == total) {
            throw new IllegalStateException("Cannot increment beyond the total of: " + total);
        }
        current++;
    }

    public String getProgress() {
        int completedWidth = (int) ((current * 1.0) / total * progressBarWidth);
        int remainingWidth = progressBarWidth - completedWidth;

        // TODO(ew): Break out suffix format as a separate thing
        return progressBarPrefix + new String(new char[completedWidth]).replace('\0', fillerChar)
            + new String(new char[remainingWidth]).replace('\0', incompleteChar)
            + progressBarSuffix + " " + (int) (current * 100.0 / total) + '%' + ' ' + suffix;
    }
}
