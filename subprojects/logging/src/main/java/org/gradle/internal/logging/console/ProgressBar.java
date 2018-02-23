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
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.StyledTextOutput;

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

    public ProgressBar(String progressBarPrefix, int progressBarWidth, String progressBarSuffix, char completeChar, char incompleteChar, String suffix, int initialProgress, int totalProgress) {
        this.progressBarPrefix = progressBarPrefix;
        this.progressBarWidth = progressBarWidth;
        this.progressBarSuffix = progressBarSuffix;
        this.fillerChar = completeChar;
        this.incompleteChar = incompleteChar;
        this.suffix = suffix;
        this.current = initialProgress;
        this.total = totalProgress;
    }

    public void update(boolean failing) {
        this.current++;
        this.failing = this.failing || failing;
    }

    public List<StyledTextOutputEvent.Span> formatProgress(int consoleCols, boolean timerEnabled, long elapsedTime) {
        int completedWidth = (int) ((current * 1.0) / total * progressBarWidth);
        int remainingWidth = progressBarWidth - completedWidth;

        String statusPrefix = trimToConsole(consoleCols, 0, progressBarPrefix);
        String coloredProgress = trimToConsole(consoleCols, statusPrefix.length(), new String(new char[completedWidth]).replace('\0', fillerChar));
        String statusSuffix = trimToConsole(consoleCols, coloredProgress.length(), new String(new char[remainingWidth]).replace('\0', incompleteChar)
            + progressBarSuffix + " " + (int) (current * 100.0 / total) + '%' + ' ' + suffix
            + (timerEnabled ? " [" + elapsedTimeFormatter.format(elapsedTime) + "]" : ""));

        return Lists.newArrayList(
            new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, statusPrefix),
            new StyledTextOutputEvent.Span(failing ? StyledTextOutput.Style.FailureHeader : StyledTextOutput.Style.SuccessHeader, coloredProgress),
            new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, statusSuffix));
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
