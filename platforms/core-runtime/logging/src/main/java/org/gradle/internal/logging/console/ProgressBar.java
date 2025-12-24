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
import org.apache.commons.lang3.StringUtils;
import org.gradle.internal.logging.events.StyledTextOutputEvent.Span;
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class ProgressBar {
    public static final int PROGRESS_BAR_WIDTH = 13;
    // Unicode progress bar style (Linux/macOS) - avoids ligature-triggering sequences
    public static final String UNICODE_PROGRESS_BAR_PREFIX = "|";
    public static final String UNICODE_PROGRESS_BAR_SUFFIX = "|";
    // ASCII progress bar style (fallback/compatibility) - simple hash-based progress for non-Unicode terminals
    public static final String ASCII_PROGRESS_BAR_PREFIX = "[";
    public static final char ASCII_PROGRESS_BAR_COMPLETE_CHAR = '#';
    public static final char ASCII_PROGRESS_BAR_INCOMPLETE_CHAR = '.';
    public static final String ASCII_PROGRESS_BAR_SUFFIX = "]";
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressBar.class);

    // Unicode block characters for smoother progress display (U+258F to U+2588)
    // Note: These characters require font support. Modern monospace fonts (Fira Code,
    // JetBrains Mono, Cascadia Code, DejaVu Sans Mono) support them well. Older fonts
    // like Courier New may only have the full block (█) and show replacement characters
    // for partial blocks. The detection logic in ConsoleMetaData.supportsUnicode() uses
    // conservative heuristics (UTF-8 locale, modern terminal detection) to avoid enabling
    // Unicode mode when fonts are unlikely to support these characters.
    private static final char[] UNICODE_BLOCKS = {
        ' ', // Empty
        '▏', // ▏ 1/8 block  '\u258F',
        '▎', // ▎ 2/8 block  '\u258E',
        '▍', // ▍ 3/8 block  '\u258D',
        '▌', // ▌ 4/8 block  '\u258C',
        '▋', // ▋ 5/8 block  '\u258B',
        '▊', // ▊ 6/8 block  '\u258A',
        '▉', // ▉ 7/8 block  '\u2589',
        '█'  // █ full block '\u2588'
    };

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
    private @Nullable ExecutorService deadlockPreventer;
    private boolean failing;
    private @Nullable String lastElapsedTimeStr;
    private @Nullable List<Span> formatted;

    public ProgressBar(
        ConsoleMetaData consoleMetaData,
        String progressBarPrefix,
        int progressBarWidth,
        String progressBarSuffix,
        char completeChar,
        char incompleteChar,
        String suffix,
        int initialProgress,
        int totalProgress
    ) {
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

    static ProgressBar createProgressBar(ConsoleMetaData consoleMetaData, String initialSuffix, int totalProgress) {
        // Use Unicode progress bars if terminal supports it, otherwise use ASCII
        if (consoleMetaData.supportsUnicode()) {
            // Unicode mode: smooth progress with block characters
            return new ProgressBar(consoleMetaData,
                UNICODE_PROGRESS_BAR_PREFIX,
                PROGRESS_BAR_WIDTH,
                UNICODE_PROGRESS_BAR_SUFFIX,
                ' ', // Not used in Unicode mode
                ' ', // Not used in Unicode mode
                initialSuffix,
                0,
                totalProgress);
        } else {
            // ASCII mode: hash-based progress for compatibility
            return new ProgressBar(consoleMetaData,
                ASCII_PROGRESS_BAR_PREFIX,
                PROGRESS_BAR_WIDTH,
                ASCII_PROGRESS_BAR_SUFFIX,
                ASCII_PROGRESS_BAR_COMPLETE_CHAR,
                ASCII_PROGRESS_BAR_INCOMPLETE_CHAR,
                initialSuffix,
                0,
                totalProgress);
        }
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
            Future<?> ignored = deadlockPreventer.submit(() -> {
                // do not do this directly or a deadlock happens
                // to prevent that deadlock, execute it separately in another thread
                LOGGER.warn("More progress was logged than there should be ({} > {})", current, total);
            });
        }
        this.failing = this.failing || failing;
        formatted = null;
    }

    public List<Span> formatProgress(boolean timerEnabled, long elapsedTime) {
        String elapsedTimeStr = elapsedTimeFormatter.format(elapsedTime);
        if (formatted != null && elapsedTimeStr.equals(lastElapsedTimeStr)) {
            return formatted;
        }

        int consoleCols = consoleMetaData.getCols();

        // Calculate progress percentage for both display and taskbar
        int progressPercent = (int) (current * 100.0 / total);

        // Prepend taskbar progress sequence (invisible control sequence)
        String taskbarSequence = buildTaskbarProgressSequence(progressPercent, failing);
        String statusPrefix = trimToConsole(consoleCols, 0, taskbarSequence + progressBarPrefix);

        if (consoleMetaData.supportsUnicode()) {
            return getUnicodeFormatted(timerEnabled, consoleCols, statusPrefix, progressPercent, elapsedTimeStr);
        } else {
            return getAsciiFormatted(timerEnabled, consoleCols, statusPrefix, progressPercent, elapsedTimeStr);
        }
    }

    private List<Span> getAsciiFormatted(boolean timerEnabled, int consoleCols, String statusPrefix, int progressPercent, String elapsedTimeStr) {

        int completedWidth = getCompletedWidth();
        int remainingWidth = progressBarWidth - completedWidth;

        String coloredProgress = trimToConsole(consoleCols, statusPrefix.length(), StringUtils.repeat(fillerChar, completedWidth));
        String statusSuffix = trimToConsole(consoleCols, statusPrefix.length() + coloredProgress.length(), StringUtils.repeat(incompleteChar, remainingWidth)
            + renderProgressStatus(timerEnabled, progressPercent, elapsedTimeStr));

        lastElapsedTimeStr = elapsedTimeStr;
        return formatted = createFormattedList(statusPrefix, coloredProgress, statusSuffix);
    }

    private List<Span> createFormattedList(String statusPrefix, String coloredProgress, String statusSuffix) {
        return ImmutableList.of(
            new Span(StyledTextOutput.Style.Header, statusPrefix),
            new Span(failing ? StyledTextOutput.Style.FailureHeader : StyledTextOutput.Style.SuccessHeader, coloredProgress),
            new Span(StyledTextOutput.Style.Header, statusSuffix));
    }

    private List<Span> getUnicodeFormatted(boolean timerEnabled, int consoleCols, String statusPrefix, int progressPercent, String elapsedTimeStr) {
        // Unicode mode: use block characters for finer granularity (8x resolution)
        String progress = getProgressString();

        String coloredProgress = trimToConsole(consoleCols, statusPrefix.length(), progress);
        String statusSuffix = trimToConsole(consoleCols, statusPrefix.length() + coloredProgress.length(),
            renderProgressStatus(timerEnabled, progressPercent, elapsedTimeStr));

        lastElapsedTimeStr = elapsedTimeStr;
        return formatted = createFormattedList(statusPrefix, coloredProgress, statusSuffix);
    }

    private String renderProgressStatus(boolean timerEnabled, int progressPercent, String elapsedTimeStr) {
        return progressBarSuffix + " " + progressPercent + '%' + ' ' + suffix
            + (timerEnabled ? " [" + elapsedTimeStr + "]" : "");
    }

    private String getProgressString() {
        double progressRatio = getProgressRatio();

        // Calculate progress in eighths (8 sub-divisions per character)
        double totalEighths = progressBarWidth * 8.0;
        int completedEighths = (int) (progressRatio * totalEighths);

        StringBuilder progress = new StringBuilder(progressBarWidth);
        for (int i = 0; i < progressBarWidth; i++) {
            int eighthsAtPosition = i * 8;
            int remainingEighths = min(8, max(0, completedEighths - eighthsAtPosition));

            progress.append(UNICODE_BLOCKS[remainingEighths]);
        }

        return progress.toString();
    }

    private int getCompletedWidth() {
        // ASCII mode: traditional hash-based progress
        if (current > total) {
            return progressBarWidth - 1;
        }
        return (int) ((double) current / total * progressBarWidth);
    }

    private double getProgressRatio() {
        if (current > total) {
            // progress was reported excessively, show almost complete
            return (progressBarWidth - 1.0) / progressBarWidth;
        }
        return (double) current / total;
    }

    private static String trimToConsole(int cols, int prefixLength, String str) {
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
        if (!consoleMetaData.supportsTaskbarProgress()) {
            return "";
        }

        // ESC ] 9 ; 4 ; state ; progress BEL
        // Using BEL (0x07) instead of ST (ESC \) for broader compatibility
        int state = isError ? 2 : 1; // 1=normal, 2=error
        return "\u001B]9;4;" + state + ";" + progressPercent + "\u0007";
    }
}
