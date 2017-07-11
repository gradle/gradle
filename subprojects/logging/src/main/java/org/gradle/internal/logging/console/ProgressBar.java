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

import com.google.common.collect.Lists;
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.Span;
import org.gradle.internal.logging.text.Style;

import java.util.List;

public class ProgressBar {
    private final TersePrettyDurationFormatter elapsedTimeFormatter = new TersePrettyDurationFormatter();

    private final String progressBarPrefix;
    private int progressBarWidth;
    private final String progressBarSuffix;
    private char fillerChar;
    private final char incompleteChar;
    private String suffix;

    private int current;
    private int total;
    private boolean failing;

    public ProgressBar(String progressBarPrefix, int progressBarWidth, String progressBarSuffix, char completeChar, char incompleteChar, String suffix) {
        this.progressBarPrefix = progressBarPrefix;
        this.progressBarWidth = progressBarWidth;
        this.progressBarSuffix = progressBarSuffix;
        this.fillerChar = completeChar;
        this.incompleteChar = incompleteChar;
        this.suffix = suffix;
    }

    public void update(int currentProgress, int totalProgress, boolean failing) {
        this.current = currentProgress;
        this.total = totalProgress;
        this.failing = this.failing || failing;
    }

    public List<Span> formatProgress(int consoleCols, boolean timerEnabled, long elapsedTime) {
        int completedWidth = (int) ((current * 1.0) / total * progressBarWidth);
        int remainingWidth = progressBarWidth - completedWidth;

        String statusPrefix = trimToConsole(consoleCols, 0, progressBarPrefix);
        String coloredProgress = trimToConsole(consoleCols, statusPrefix.length(), new String(new char[completedWidth]).replace('\0', fillerChar));
        String statusSuffix = trimToConsole(consoleCols, coloredProgress.length(), new String(new char[remainingWidth]).replace('\0', incompleteChar)
            + progressBarSuffix + " " + (int) (current * 100.0 / total) + '%' + ' ' + suffix
            + (timerEnabled ? " [" + elapsedTimeFormatter.format(elapsedTime) + "]" : ""));

        return Lists.newArrayList(
            new Span(Style.of(Style.Emphasis.BOLD), statusPrefix),
            new Span(Style.of(Style.Emphasis.BOLD, failing ? Style.Color.RED : Style.Color.GREEN), coloredProgress),
            new Span(Style.of(Style.Emphasis.BOLD), statusSuffix));
    }

    private String trimToConsole(int cols, int prefixLength, String str) {
        int consoleWidth = cols - 1;
        int remainingWidth = consoleWidth - prefixLength;

        if (consoleWidth < 0) {
            return str;
        }
        if (remainingWidth <= 0) {
            return "";
        }
        if (consoleWidth < str.length()) {
            return str.substring(0, consoleWidth);
        }
        return str;
    }
}
