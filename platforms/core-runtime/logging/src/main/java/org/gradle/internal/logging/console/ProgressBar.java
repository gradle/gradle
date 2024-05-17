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
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProgressBar {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressBar.class);

    private final TersePrettyDurationFormatter elapsedTimeFormatter = new TersePrettyDurationFormatter();

    private final ConsoleMetaData consoleMetaData;
    private final String progressBarPrefix;
    private final int progressBarWidth;
    private final String progressBarSuffix;
    private final char fillerChar;
    private final char incompleteChar;
    private final String suffix;

    private int current;
    private int total;
    private ExecutorService deadlockPreventer;
    private boolean failing;
    private String lastElapsedTimeStr;
    private List<StyledTextOutputEvent.Span> formatted;

    public ProgressBar(ConsoleMetaData consoleMetaData, String progressBarPrefix, int progressBarWidth, String progressBarSuffix, char completeChar, char incompleteChar, String suffix, int initialProgress, int totalProgress) {
        this.consoleMetaData = consoleMetaData;
        this.progressBarPrefix = progressBarPrefix;
        this.progressBarWidth = progressBarWidth;
        this.progressBarSuffix = progressBarSuffix;
        this.fillerChar = completeChar;
        this.incompleteChar = incompleteChar;
        this.suffix = suffix;
        this.current = initialProgress;
        this.total = totalProgress;
    }

    public void moreProgress(int totalProgress) {
        total += totalProgress;
        formatted = null;
    }

    public void update(boolean failing) {
        this.current++;
        if (current > total) {
            if (deadlockPreventer == null) {
                deadlockPreventer = Executors.newSingleThreadExecutor();
            }
            deadlockPreventer.submit(new Runnable() {
                @Override
                public void run() {
                    // do not do this directly or a deadlock happens
                    // to prevent that deadlock, execute it separately in another thread
                    LOGGER.warn("More progress was logged than there should be ({} > {})", current, total);
                }
            });
        }
        this.failing = this.failing || failing;
        formatted = null;
    }

    public List<StyledTextOutputEvent.Span> formatProgress(boolean timerEnabled, long elapsedTime) {
        String elapsedTimeStr = elapsedTimeFormatter.format(elapsedTime);
        if (formatted == null || !elapsedTimeStr.equals(lastElapsedTimeStr)) {
            int consoleCols = consoleMetaData.getCols();
            int completedWidth;
            if (current > total) {
                // progress was reported excessively,
                // we do not know how much work really is left,
                // so we at least show one progress bar tick as unfinished
                completedWidth = progressBarWidth - 1;
            } else {
                completedWidth = (int) ((double) current / total * progressBarWidth);
            }
            int remainingWidth = progressBarWidth - completedWidth;

            String statusPrefix = trimToConsole(consoleCols, 0, progressBarPrefix);
            String coloredProgress = trimToConsole(consoleCols, statusPrefix.length(), fill(fillerChar, completedWidth));
            String statusSuffix = trimToConsole(consoleCols, coloredProgress.length(), fill(incompleteChar, remainingWidth)
                + progressBarSuffix + " " + (int) (current * 100.0 / total) + '%' + ' ' + suffix
                + (timerEnabled ? " [" + elapsedTimeStr + "]" : ""));

            lastElapsedTimeStr = elapsedTimeStr;
            formatted = Lists.newArrayList(
                new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, statusPrefix),
                new StyledTextOutputEvent.Span(failing ? StyledTextOutput.Style.FailureHeader : StyledTextOutput.Style.SuccessHeader, coloredProgress),
                new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, statusSuffix));
        }
        return formatted;
    }

    private String fill(char ch, int count) {
        char[] chars = new char[count];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ch;
        }
        return new String(chars);
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
