/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.protocol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rebuilds the full description of a failure, the text of its whole cause subtree, from the per-node own descriptions.
 * <p>
 * Each own description is parent independent. This walk reapplies the parent relative stack trace tail elision so the
 * result matches what a single recursive print would produce.
 */
@NullMarked
public final class FailureDescriptionReconstructor {

    private static final String FRAME_PREFIX = "\tat ";
    private static final String SUPPRESSED_PREFIX = "\tSuppressed: ";
    // Only used for the synthesized "... N more" elision line; every other line is re-emitted with its own original
    // terminator (see splitLines), so the reconstruction reproduces the printer's separators rather than normalizing
    // them. The daemon prints with its own line separator and the daemon is always local, so the local one matches it.
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final Pattern LINE_TERMINATOR = Pattern.compile("\\R");

    private FailureDescriptionReconstructor() {
    }

    public static <T> String reconstruct(
        T root,
        Function<? super T, String> ownDescription,
        Function<? super T, ? extends List<? extends T>> causes
    ) {
        StringBuilder builder = new StringBuilder();
        append(builder, "", root, null, ownDescription, causes);
        return builder.toString();
    }

    private static <T> void append(
        StringBuilder builder,
        String caption,
        T node,
        @Nullable List<String> parentFrames,
        Function<? super T, String> ownDescription,
        Function<? super T, ? extends List<? extends T>> causes
    ) {
        List<Line> lines = splitLines(ownDescription.apply(node));

        // The header (message) can span several tab-indented lines, so it ends at the first own frame or "Suppressed:"
        // caption, not at any tab. A message line shaped exactly like a frame ("\tat ...") is indistinguishable from a
        // real frame here, the one case this text-only reconstruction can misclassify.
        int framesStart = 0;
        while (framesStart < lines.size() && !isFrame(lines.get(framesStart)) && !isSuppressed(lines.get(framesStart))) {
            framesStart++;
        }
        int framesEnd = framesStart;
        while (framesEnd < lines.size() && isFrame(lines.get(framesEnd))) {
            framesEnd++;
        }
        List<Line> header = lines.subList(0, framesStart);
        List<Line> frames = lines.subList(framesStart, framesEnd);
        List<Line> tail = lines.subList(framesEnd, lines.size());

        List<String> frameContents = contentsOf(frames);
        int common = parentFrames == null ? 0 : commonTailSize(frameContents, parentFrames);

        builder.append(caption);
        appendLines(builder, header);
        appendLines(builder, frames.subList(0, frames.size() - common));
        if (common > 0) {
            builder.append("\t... ").append(common).append(" more").append(LINE_SEPARATOR);
        }
        appendLines(builder, tail);

        List<? extends T> nodeCauses = causes.apply(node);
        if (nodeCauses.size() == 1) {
            append(builder, "Caused by: ", nodeCauses.get(0), frameContents, ownDescription, causes);
        } else {
            for (int c = 0; c < nodeCauses.size(); c++) {
                append(builder, "Cause " + (c + 1) + ": ", nodeCauses.get(c), frameContents, ownDescription, causes);
            }
        }
    }

    private static void appendLines(StringBuilder builder, List<Line> lines) {
        for (Line line : lines) {
            builder.append(line.content).append(terminatorOf(line));
        }
    }

    private static List<String> contentsOf(List<Line> lines) {
        List<String> contents = new ArrayList<>(lines.size());
        for (Line line : lines) {
            contents.add(line.content);
        }
        return contents;
    }

    private static boolean isFrame(Line line) {
        return line.content.startsWith(FRAME_PREFIX);
    }

    private static boolean isSuppressed(Line line) {
        return line.content.startsWith(SUPPRESSED_PREFIX);
    }

    private static String terminatorOf(Line line) {
        // The printer terminates every line, so a captured terminator is always present; fall back defensively to the
        // platform separator only if an own description ever ended without one.
        return line.terminator.isEmpty() ? LINE_SEPARATOR : line.terminator;
    }

    private static int commonTailSize(List<String> frames, List<String> parentFrames) {
        int i = frames.size() - 1;
        int j = parentFrames.size() - 1;
        int common = 0;
        while (i >= 0 && j >= 0 && frames.get(i).equals(parentFrames.get(j))) {
            i--;
            j--;
            common++;
        }
        return common;
    }

    private static List<Line> splitLines(String text) {
        List<Line> lines = new ArrayList<>();
        Matcher matcher = LINE_TERMINATOR.matcher(text);
        int start = 0;
        while (matcher.find()) {
            lines.add(new Line(text.substring(start, matcher.start()), text.substring(matcher.start(), matcher.end())));
            start = matcher.end();
        }
        // A final line terminator leaves no trailing content, so there is no extra blank line to add. Trailing content
        // without a terminator (not produced by the printer, which always ends a line) is kept as a last line.
        if (start < text.length()) {
            lines.add(new Line(text.substring(start), ""));
        }
        return lines;
    }

    /**
     * A line of an own description together with the exact terminator that followed it. Preserving the terminator lets
     * reconstruction reproduce the printer's separators, including a message's embedded newline that differs from the
     * platform separator (e.g. a multi-line message on Windows), rather than normalizing every line break to the
     * platform separator.
     */
    private static final class Line {
        final String content;
        final String terminator;

        Line(String content, String terminator) {
            this.content = content;
            this.terminator = terminator;
        }
    }
}
