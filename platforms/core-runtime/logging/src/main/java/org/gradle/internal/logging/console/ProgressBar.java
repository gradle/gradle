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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ProgressBar {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressBar.class);

    // Unicode block characters for smoother progress display (U+258F to U+2588)
    // Note: These characters require font support. Modern monospace fonts (Fira Code,
    // JetBrains Mono, Cascadia Code, DejaVu Sans Mono) support them well. Older fonts
    // like Courier New may only have the full block (█) and show replacement characters
    // for partial blocks. The detection logic in ConsoleMetaData.supportsUnicode() uses
    // conservative heuristics (UTF-8 locale, modern terminal detection) to avoid enabling
    // Unicode mode when fonts are unlikely to support these characters.
    private static final char[] UNICODE_BLOCKS = {
        ' ',    // Empty
        '\u258F', // ▏ 1/8 block
        '\u258E', // ▎ 2/8 block
        '\u258D', // ▍ 3/8 block
        '\u258C', // ▌ 4/8 block
        '\u258B', // ▋ 5/8 block
        '\u258A', // ▊ 6/8 block
        '\u2589', // ▉ 7/8 block
        '\u2588'  // █ full block
    };

    private final TersePrettyDurationFormatter elapsedTimeFormatter = new TersePrettyDurationFormatter();

    private final ConsoleMetaData consoleMetaData;
    private final String progressBarPrefix;
    private final int progressBarWidth;
    private final String progressBarSuffix;
    private final char fillerChar;
    private final char incompleteChar;
    private final String suffix;
    private final boolean useUnicode;
    private final boolean useTaskbarProgress;

    private int current;
    private int total;
    private ExecutorService deadlockPreventer;
    private boolean failing;
    private String lastElapsedTimeStr;
    private List<StyledTextOutputEvent.Span> formatted;

    public ProgressBar(ConsoleMetaData consoleMetaData, String progressBarPrefix, int progressBarWidth, String progressBarSuffix, char completeChar, char incompleteChar, String suffix, int initialProgress, int totalProgress) {
        this(consoleMetaData, progressBarPrefix, progressBarWidth, progressBarSuffix, completeChar, incompleteChar, suffix, initialProgress, totalProgress, false);
    }

    public ProgressBar(ConsoleMetaData consoleMetaData, String progressBarPrefix, int progressBarWidth, String progressBarSuffix, char completeChar, char incompleteChar, String suffix, int initialProgress, int totalProgress, boolean useUnicode) {
        this.consoleMetaData = consoleMetaData;
        this.progressBarPrefix = progressBarPrefix;
        this.progressBarWidth = progressBarWidth;
        this.progressBarSuffix = progressBarSuffix;
        this.fillerChar = completeChar;
        this.incompleteChar = incompleteChar;
        this.suffix = suffix;
        this.current = initialProgress;
        this.total = totalProgress;
        this.useUnicode = useUnicode;
        this.useTaskbarProgress = consoleMetaData.supportsTaskbarProgress();
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
            Future<?> ignored = deadlockPreventer.submit(new Runnable() {
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

            // Calculate progress percentage for both display and taskbar
            int progressPercent = (int) (current * 100.0 / total);

            // Prepend taskbar progress sequence (invisible control sequence)
            String taskbarSequence = buildTaskbarProgressSequence(progressPercent, failing);
            String statusPrefix = trimToConsole(consoleCols, 0, taskbarSequence + progressBarPrefix);
            String coloredProgress;
            String statusSuffix;

            if (useUnicode) {
                // Unicode mode: use block characters for finer granularity (8x resolution)
                double progressRatio;
                if (current > total) {
                    // progress was reported excessively, show almost complete
                    progressRatio = (progressBarWidth - 1.0) / progressBarWidth;
                } else {
                    progressRatio = (double) current / total;
                }

                // Calculate progress in eighths (8 sub-divisions per character)
                double totalEighths = progressBarWidth * 8.0;
                int completedEighths = (int) (progressRatio * totalEighths);

                StringBuilder progress = new StringBuilder();
                for (int i = 0; i < progressBarWidth; i++) {
                    int eighthsAtPosition = i * 8;
                    if (completedEighths >= eighthsAtPosition + 8) {
                        // Full block
                        progress.append(UNICODE_BLOCKS[8]);
                    } else if (completedEighths > eighthsAtPosition) {
                        // Partial block
                        int partialEighths = completedEighths - eighthsAtPosition;
                        progress.append(UNICODE_BLOCKS[partialEighths]);
                    } else {
                        // Empty space
                        progress.append(UNICODE_BLOCKS[0]);
                    }
                }

                coloredProgress = trimToConsole(consoleCols, statusPrefix.length(), progress.toString());
            } else {
                // ASCII mode: traditional hash-based progress
                int completedWidth;
                if (current > total) {
                    completedWidth = progressBarWidth - 1;
                } else {
                    completedWidth = (int) ((double) current / total * progressBarWidth);
                }
                int remainingWidth = progressBarWidth - completedWidth;

                coloredProgress = trimToConsole(consoleCols, statusPrefix.length(), fill(fillerChar, completedWidth));
                statusSuffix = trimToConsole(consoleCols, statusPrefix.length() + coloredProgress.length(), fill(incompleteChar, remainingWidth)
                    + progressBarSuffix + " " + progressPercent + '%' + ' ' + suffix
                    + (timerEnabled ? " [" + elapsedTimeStr + "]" : ""));

                lastElapsedTimeStr = elapsedTimeStr;
                formatted = ImmutableList.of(
                    new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, statusPrefix),
                    new StyledTextOutputEvent.Span(failing ? StyledTextOutput.Style.FailureHeader : StyledTextOutput.Style.SuccessHeader, coloredProgress),
                    new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, statusSuffix));
                return formatted;
            }

            statusSuffix = trimToConsole(consoleCols, statusPrefix.length() + coloredProgress.length(),
                progressBarSuffix + " " + progressPercent + '%' + ' ' + suffix
                + (timerEnabled ? " [" + elapsedTimeStr + "]" : ""));

            lastElapsedTimeStr = elapsedTimeStr;
            formatted = ImmutableList.of(
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

    /**
     * Generates OSC 9;4 sequence for taskbar progress (ConEmu, Ghostty).
     * Format: ESC ] 9 ; 4 ; state ; progress ST
     * States: 0=remove, 1=normal, 2=error, 3=indeterminate, 4=paused
     */
    private String buildTaskbarProgressSequence(int progressPercent, boolean isError) {
        if (!useTaskbarProgress) {
            return "";
        }

        // ESC ] 9 ; 4 ; state ; progress BEL
        // Using BEL (0x07) instead of ST (ESC \) for broader compatibility
        int state = isError ? 2 : 1; // 1=normal, 2=error
        return "\u001B]9;4;" + state + ";" + progressPercent + "\u0007";
    }

//    /**
//     * Generates OSC 9;4 sequence to remove taskbar progress indicator.
//     */
//    private String clearTaskbarProgress() {
//        if (!useTaskbarProgress) {
//            return "";
//        }
//        // ESC ] 9 ; 4 ; 0 BEL (state 0 = remove progress)
//        return "\u001B]9;4;0\u0007";
//    }
}
