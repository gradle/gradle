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

import org.apache.commons.lang3.StringUtils;
import org.gradle.internal.logging.events.StyledTextOutputEvent.Span;
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The progress bar line of the rich console: the bar, the phase label, the running and queued counts and the build duration.
 *
 * <pre>
 * │██████▉▶······················│» EXECUTING  ▶ 8  … 5,312▲  [1m 12s]
 * </pre>
 *
 * <p>The bar has three segments: done, running and queued work. They are scaled with {@link AdaptiveBarScale}.
 * The last cell is kept for work that is not known yet, so the bar is never full while the build runs.
 * The open edge pulses while the total is not final. The flag after the queued count shows that the total just grew.</p>
 */
public class ProgressBar {
    // Unicode progress bar style (Linux/macOS) - avoids ligature-triggering sequences
    public static final String UNICODE_PROGRESS_BAR_PREFIX = "│";
    public static final String UNICODE_PROGRESS_BAR_SUFFIX = "│";
    // ASCII progress bar style (fallback/compatibility) - for non-Unicode terminals
    public static final String ASCII_PROGRESS_BAR_PREFIX = "[";
    public static final String ASCII_PROGRESS_BAR_SUFFIX = "]";
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressBar.class);

    private static final int MAX_BAR_WIDTH = 30;
    private static final int MIN_BAR_WIDTH = 15;
    private static final long FLAG_MILLIS = 2500;
    private static final long PULSE_MILLIS = 500;
    // The console counts the taskbar sequence as text, so the width leaves room for the longest one
    private static final int TASKBAR_SEQUENCE_MAX_LENGTH = buildOsc94Sequence("2;100").length();

    // Note: The block characters require font support. Modern monospace fonts (Fira Code,
    // JetBrains Mono, Cascadia Code, DejaVu Sans Mono) support them well. Older fonts
    // like Courier New may only have the full block (█) and show replacement characters
    // for partial blocks. The detection logic in ConsoleMetaData.supportsUnicode() uses
    // conservative heuristics (UTF-8 locale, modern terminal detection) to avoid enabling
    // Unicode mode when fonts are unlikely to support these characters.
    private static final Glyphs UNICODE_GLYPHS = new Glyphs(
        UNICODE_PROGRESS_BAR_PREFIX, UNICODE_PROGRESS_BAR_SUFFIX, '█', new char[]{'▏', '▎', '▍', '▌', '▋', '▊', '▉'}, '▶', '·', "»", "▲", true
    );
    private static final Glyphs ASCII_GLYPHS = new Glyphs(
        ASCII_PROGRESS_BAR_PREFIX, ASCII_PROGRESS_BAR_SUFFIX, '#', null, '=', '.', ">", "^", false
    );

    /**
     * How the open edge after the bar is shown.
     */
    enum Edge {
        /**
         * The total is not final yet.
         */
        PULSING,
        /**
         * More work can still arrive.
         */
        STEADY,
        /**
         * No work is going on, so there is no edge and no counts.
         */
        NONE
    }

    private final TersePrettyDurationFormatter elapsedTimeFormatter = new TersePrettyDurationFormatter();
    private final AdaptiveBarScale scale = new AdaptiveBarScale();

    private final ConsoleMetaData consoleMetaData;
    private final Glyphs glyphs;
    private String phase;
    private Edge edge = Edge.STEADY;

    private int done;
    private int running;
    private int total;
    private boolean failing;
    private @Nullable ExecutorService deadlockPreventer;

    private int previousTotal = -1;
    private long flagUntil = Long.MIN_VALUE;
    private int runningCountWidth;
    private int queuedCountWidth;

    private ProgressBar(ConsoleMetaData consoleMetaData, Glyphs glyphs, String phase, int totalProgress) {
        this.consoleMetaData = consoleMetaData;
        this.glyphs = glyphs;
        this.phase = phase;
        this.total = totalProgress;
    }

    static ProgressBar createProgressBar(ConsoleMetaData consoleMetaData, String initialPhase, int totalProgress) {
        // Use Unicode progress bars if terminal supports it, otherwise use ASCII
        if (consoleMetaData.supportsUnicode()) {
            return getUnicodeProgressBar(consoleMetaData, initialPhase, totalProgress);
        } else {
            return getAsciiProgressBar(consoleMetaData, initialPhase, totalProgress);
        }
    }

    static ProgressBar getUnicodeProgressBar(ConsoleMetaData consoleMetaData, String initialPhase, int totalProgress) {
        return new ProgressBar(consoleMetaData, UNICODE_GLYPHS, initialPhase, totalProgress);
    }

    static ProgressBar getAsciiProgressBar(ConsoleMetaData consoleMetaData, String initialPhase, int totalProgress) {
        return new ProgressBar(consoleMetaData, ASCII_GLYPHS, initialPhase, totalProgress);
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    void setEdge(Edge edge) {
        this.edge = edge;
    }

    public void setRunning(int running) {
        this.running = running;
    }

    public void moreProgress(int totalProgress) {
        total += totalProgress;
    }

    public void update(boolean failing) {
        this.done++;
        if (done > total) {
            if (deadlockPreventer == null) {
                deadlockPreventer = Executors.newSingleThreadExecutor();
            }
            Future<?> ignored = deadlockPreventer.submit(() -> {
                // do not do this directly or a deadlock happens
                // to prevent that deadlock, execute it separately in another thread
                LOGGER.warn("More progress was logged than there should be ({} > {})", done, total);
            });
        }
        this.failing = this.failing || failing;
    }

    /**
     * Formats the line for a new frame.
     *
     * @param elapsedTime the build duration, also used as the clock for the animation
     */
    public List<Span> formatProgress(boolean timerEnabled, long elapsedTime) {
        int queued = Math.max(0, total - done - running);
        int totalChange = previousTotal < 0 ? 0 : Integer.compare(total, previousTotal);
        if (totalChange > 0 && previousTotal > 0) {
            flagUntil = elapsedTime + FLAG_MILLIS;
        }
        previousTotal = total;

        Spans textAfterBar = textAfterBar(timerEnabled, elapsedTime, queued);
        int width = barWidth(textAfterBar.length());
        // The last cell is kept for work that is not known yet, so the bar is never full while the build runs
        int countedWidth = width - 1;
        double exponent = scale.next(done, running, queued, totalChange, elapsedTime, countedWidth);
        AdaptiveBarScale.Cells cells = AdaptiveBarScale.cells(countedWidth, done, running, queued, exponent);

        int fullCells;
        int partialEighths;
        if (glyphs.partialBlocks != null) {
            int doneEighths = (int) (cells.done * 8);
            fullCells = doneEighths / 8;
            partialEighths = doneEighths % 8;
        } else {
            fullCells = (int) cells.done;
            partialEighths = 0;
        }
        int runningCells = running > 0 ? Math.max(1, (int) Math.round(cells.running)) : 0;
        int usedCells = fullCells + (partialEighths > 0 ? 1 : 0);
        if (usedCells + runningCells > countedWidth) {
            // Rounding made the segments too wide: drop the partial cell first, then running cells
            partialEighths = 0;
            usedCells = fullCells;
            if (usedCells + runningCells > countedWidth) {
                runningCells = Math.max(1, countedWidth - usedCells);
                fullCells = countedWidth - runningCells;
                usedCells = fullCells;
            }
        }
        int queuedCells = countedWidth - usedCells - runningCells + 1;
        int drawnDonePercent = (fullCells * 8 + partialEighths) * 100 / (width * 8);

        Spans line = new Spans();
        line.append(StyledTextOutput.Style.Header, buildTaskbarProgressSequence(drawnDonePercent, failing) + glyphs.barStart);
        line.append(failing ? StyledTextOutput.Style.FailureHeader : StyledTextOutput.Style.SuccessHeader,
            StringUtils.repeat(glyphs.done, fullCells) + (partialEighths > 0 ? String.valueOf(glyphs.partialBlock(partialEighths)) : ""));
        line.append(StyledTextOutput.Style.ProgressStatus, StringUtils.repeat(glyphs.running, runningCells));
        line.append(StyledTextOutput.Style.Header, StringUtils.repeat(glyphs.queued, queuedCells) + glyphs.barEnd);
        line.append(textAfterBar);
        return line.trimmedTo(consoleMetaData.getCols() - 1);
    }

    private Spans textAfterBar(boolean timerEnabled, long now, int queued) {
        Spans text = new Spans();
        if (edge != Edge.NONE) {
            boolean bold = edge == Edge.STEADY || (now / PULSE_MILLIS) % 2 == 0;
            text.append(bold ? StyledTextOutput.Style.Header : StyledTextOutput.Style.Normal, glyphs.edge);
        }
        text.append(StyledTextOutput.Style.Header, " " + phase);
        if (total > 0 && edge != Edge.NONE) {
            String runningCount = formatCount(running);
            String queuedCount = formatCount(queued);
            runningCountWidth = Math.max(runningCountWidth, runningCount.length());
            queuedCountWidth = Math.max(queuedCountWidth, queuedCount.length());
            runningCount = StringUtils.leftPad(runningCount, runningCountWidth);
            queuedCount = StringUtils.leftPad(queuedCount, queuedCountWidth);
            String flag = now < flagUntil ? glyphs.flag : " ";
            if (glyphs.unicode) {
                text.append(StyledTextOutput.Style.Header, "  ");
                text.append(StyledTextOutput.Style.ProgressStatus, String.valueOf(glyphs.running));
                text.append(StyledTextOutput.Style.Header, " " + runningCount + "  … " + queuedCount + flag);
            } else {
                text.append(StyledTextOutput.Style.Header, "  " + runningCount + " running, " + queuedCount + " queued" + flag);
            }
        }
        if (timerEnabled) {
            text.append(StyledTextOutput.Style.Header, "  [" + elapsedTimeFormatter.format(now) + "]");
        }
        return text;
    }

    private int barWidth(int textAfterBarLength) {
        int cols = consoleMetaData.getCols();
        if (cols <= 0) {
            return MAX_BAR_WIDTH;
        }
        int taskbarSequenceLength = consoleMetaData.supportsTaskbarProgress() ? TASKBAR_SEQUENCE_MAX_LENGTH : 0;
        // Don't write to the right-most column, as on some consoles the cursor wraps to the next line
        int available = cols - 1 - taskbarSequenceLength - glyphs.barStart.length() - glyphs.barEnd.length() - textAfterBarLength;
        return Math.max(MIN_BAR_WIDTH, Math.min(MAX_BAR_WIDTH, available));
    }

    private static String formatCount(int count) {
        return String.format(Locale.ROOT, "%,d", count);
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
        int state = isError ? 2 : 1; // 1=normal, 2=error
        return buildOsc94Sequence(state + ";" + progressPercent);
    }

    /**
     * Returns the OSC 9;4;0 sequence to remove taskbar progress (ConEmu, Ghostty),
     * or an empty string if the terminal does not support taskbar progress.
     * Should be sent when the build ends or is interrupted (e.g. SIGINT).
     */
    public static String buildTaskbarProgressResetSequence(ConsoleMetaData consoleMetaData) {
        if (!consoleMetaData.supportsTaskbarProgress()) {
            return "";
        }
        // State 0 = remove; progress field is omitted as it is not applicable
        return buildOsc94Sequence("0");
    }

    // ESC ] 9 ; 4 ; state [; progress] BEL
    // Using BEL (0x07) instead of ST (ESC \) for broader compatibility
    private static String buildOsc94Sequence(String command) {
        return "\u001B]9;4;" + command + "\u0007";
    }

    private static final class Glyphs {
        final String barStart;
        final String barEnd;
        final char done;
        final char @Nullable [] partialBlocks;
        final char running;
        final char queued;
        final String edge;
        final String flag;
        final boolean unicode;

        Glyphs(String barStart, String barEnd, char done, char @Nullable [] partialBlocks, char running, char queued, String edge, String flag, boolean unicode) {
            this.barStart = barStart;
            this.barEnd = barEnd;
            this.done = done;
            this.partialBlocks = partialBlocks;
            this.running = running;
            this.queued = queued;
            this.edge = edge;
            this.flag = flag;
            this.unicode = unicode;
        }

        char partialBlock(int eighths) {
            return partialBlocks == null ? done : partialBlocks[eighths - 1];
        }
    }

    /**
     * Spans that merge text of the same style and skip empty text.
     */
    private static final class Spans {
        private final List<Span> spans = new ArrayList<>();

        void append(StyledTextOutput.Style style, String text) {
            if (text.isEmpty()) {
                return;
            }
            int last = spans.size() - 1;
            if (last >= 0 && spans.get(last).getStyle() == style) {
                spans.set(last, new Span(style, spans.get(last).getText() + text));
            } else {
                spans.add(new Span(style, text));
            }
        }

        void append(Spans other) {
            for (Span span : other.spans) {
                append(span.getStyle(), span.getText());
            }
        }

        int length() {
            int length = 0;
            for (Span span : spans) {
                length += span.getText().length();
            }
            return length;
        }

        List<Span> trimmedTo(int maxLength) {
            if (maxLength < 0) {
                return spans;
            }
            List<Span> trimmed = new ArrayList<>();
            int length = 0;
            for (Span span : spans) {
                if (length >= maxLength) {
                    break;
                }
                String text = span.getText();
                if (length + text.length() > maxLength) {
                    text = text.substring(0, maxLength - length);
                }
                trimmed.add(new Span(span.getStyle(), text));
                length += text.length();
            }
            return trimmed;
        }
    }
}
