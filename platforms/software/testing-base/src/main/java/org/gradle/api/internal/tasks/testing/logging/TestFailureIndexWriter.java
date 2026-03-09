/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestFailureDetails;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.api.tasks.testing.source.ClassSource;
import org.gradle.api.tasks.testing.source.FilePosition;
import org.gradle.api.tasks.testing.source.FileSource;
import org.gradle.api.tasks.testing.source.MethodSource;
import org.gradle.api.tasks.testing.source.TestSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes a structured JSON failure index file for test failures.
 *
 * This listener collects test failures and stdout/stderr output during test execution,
 * and writes a JSON file on root suite completion. The failure index provides structured,
 * machine-parseable data that AI agents and other tools can use to diagnose test failures
 * without parsing console output or HTML reports.
 */
@NullMarked
public class TestFailureIndexWriter implements TestListenerInternal {

    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final int MAX_EXPECTED_ACTUAL_LENGTH = 1024;
    private static final int MAX_STDOUT_LINES = 20;
    private static final int MAX_STDERR_LINES = 20;
    private static final int MAX_STACKTRACE_FRAMES = 10;

    private final Path outputFile;
    private final List<FailureEntry> failures = new ArrayList<>();
    private final Map<Object, OutputBuffer> outputBuffers = new ConcurrentHashMap<>();

    public TestFailureIndexWriter(Path outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        // ignored
    }

    @Override
    public void completed(TestDescriptorInternal descriptor, TestResult result, TestCompleteEvent completeEvent) {
        if (!descriptor.isComposite() && result.getResultType() == TestResult.ResultType.FAILURE) {
            OutputBuffer buffer = outputBuffers.remove(descriptor.getId());
            for (TestFailure failure : result.getFailures()) {
                failures.add(new FailureEntry(descriptor, failure, buffer));
            }
        } else if (descriptor.getParent() == null) {
            // Root suite completed — write the index if there were failures
            outputBuffers.clear();
            if (!failures.isEmpty()) {
                writeIndex();
            }
        }
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        if (!testDescriptor.isComposite()) {
            outputBuffers.computeIfAbsent(testDescriptor.getId(), k -> new OutputBuffer())
                .add(event);
        }
    }

    @Override
    public void metadata(TestDescriptorInternal testDescriptor, TestMetadataEvent event) {
        // ignored
    }

    /**
     * Returns the path to the failure index file.
     */
    public Path getOutputFile() {
        return outputFile;
    }

    /**
     * Returns whether any failures have been recorded.
     */
    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    private void writeIndex() {
        try {
            Files.createDirectories(outputFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                writeJson(writer);
            }
        } catch (IOException e) {
            // Don't fail the build if we can't write the index — it's supplementary output
        }
    }

    private void writeJson(BufferedWriter writer) throws IOException {
        writer.write("{\n");
        writer.write("  \"version\": 1,\n");
        writer.write("  \"failures\": [\n");

        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                writer.write(",\n");
            }
            writeFailureEntry(writer, failures.get(i));
        }

        writer.write("\n  ]\n");
        writer.write("}\n");
    }

    private void writeFailureEntry(BufferedWriter writer, FailureEntry entry) throws IOException {
        TestDescriptorInternal descriptor = entry.descriptor;
        TestFailureDetails details = entry.failure.getDetails();

        writer.write("    {\n");

        // Test identity
        writeJsonField(writer, "test", toEventPath(descriptor), true);
        writeJsonField(writer, "className", descriptor.getClassName(), true);
        writeJsonField(writer, "displayName", descriptor.getDisplayName(), true);

        // Source info
        writeSourceBlock(writer, descriptor);

        // Failure details
        writeFailureBlock(writer, details, descriptor.getClassName());

        // Output
        writeOutputBlock(writer, entry.outputBuffer);

        // Filter for re-running
        String filter = buildTestFilter(descriptor);
        if (filter != null) {
            writeJsonField(writer, "filter", filter, false);
        }

        writer.write("\n    }");
    }

    private void writeSourceBlock(BufferedWriter writer, TestDescriptor descriptor) throws IOException {
        TestSource source = descriptor.getSource();

        writer.write("      \"source\": ");

        if (source instanceof FileSource) {
            FileSource fileSource = (FileSource) source;
            writer.write("{\n");
            writeJsonField(writer, "type", "file", true, 8);
            writeJsonField(writer, "file", fileSource.getFile().getPath(), true, 8);
            FilePosition position = fileSource.getPosition();
            if (position != null) {
                writer.write("        \"line\": " + position.getLine());
                Integer column = position.getColumn();
                if (column != null) {
                    writer.write(",\n        \"column\": " + column);
                }
                writer.write("\n");
            }
            writer.write("      },\n");
        } else if (source instanceof MethodSource) {
            MethodSource methodSource = (MethodSource) source;
            writer.write("{\n");
            writeJsonField(writer, "type", "method", true, 8);
            writeJsonField(writer, "className", methodSource.getClassName(), true, 8);
            writeJsonField(writer, "methodName", methodSource.getMethodName(), false, 8);
            writer.write("\n      },\n");
        } else if (source instanceof ClassSource) {
            ClassSource classSource = (ClassSource) source;
            writer.write("{\n");
            writeJsonField(writer, "type", "class", true, 8);
            writeJsonField(writer, "className", classSource.getClassName(), false, 8);
            writer.write("\n      },\n");
        } else {
            writer.write("null,\n");
        }
    }

    private void writeFailureBlock(BufferedWriter writer, TestFailureDetails details, @Nullable String testClassName) throws IOException {
        writer.write("      \"failure\": {\n");

        // Failure type
        String type = details.isAssertionFailure() ? "assertion" : "framework";
        writeJsonField(writer, "type", type, true, 8);
        writeJsonField(writer, "exceptionClass", details.getClassName(), true, 8);

        // Message with truncation
        String message = details.getMessage();
        if (message != null) {
            boolean messageComplete = message.length() <= MAX_MESSAGE_LENGTH;
            String truncatedMessage = messageComplete ? message : message.substring(0, MAX_MESSAGE_LENGTH);
            writeJsonField(writer, "message", truncatedMessage, true, 8);
            writer.write("        \"messageComplete\": " + messageComplete + ",\n");
        } else {
            writer.write("        \"message\": null,\n");
            writer.write("        \"messageComplete\": true,\n");
        }

        // Expected/actual for assertion failures
        if (details.isAssertionFailure()) {
            String expected = details.getExpected();
            String actual = details.getActual();
            boolean isComparison = expected != null && actual != null;
            writer.write("        \"isComparison\": " + isComparison + ",\n");
            if (expected != null) {
                writeJsonField(writer, "expected", truncate(expected, MAX_EXPECTED_ACTUAL_LENGTH), true, 8);
            }
            if (actual != null) {
                writeJsonField(writer, "actual", truncate(actual, MAX_EXPECTED_ACTUAL_LENGTH), true, 8);
            }
        }

        // Filtered stacktrace
        String stacktrace = details.getStacktrace();
        String filteredStacktrace = filterStackTrace(stacktrace, testClassName);
        int filteredFrameCount = countFrames(filteredStacktrace);
        int totalFrameCount = countFrames(stacktrace);
        boolean stackTraceComplete = filteredFrameCount >= totalFrameCount;

        writeJsonField(writer, "stackTrace", filteredStacktrace, true, 8);
        writer.write("        \"stackTraceComplete\": " + stackTraceComplete + ",\n");
        writer.write("        \"stackTraceFrameCount\": " + filteredFrameCount + ",\n");
        writer.write("        \"totalFrameCount\": " + totalFrameCount);

        writer.write("\n      },\n");
    }

    private void writeOutputBlock(BufferedWriter writer, @Nullable OutputBuffer buffer) throws IOException {
        writer.write("      \"output\": {\n");

        if (buffer != null) {
            List<String> stdoutLines = buffer.getStdoutLines();
            List<String> stderrLines = buffer.getStderrLines();

            int totalStdoutLines = stdoutLines.size();
            int totalStderrLines = stderrLines.size();

            boolean stdoutComplete = totalStdoutLines <= MAX_STDOUT_LINES;
            boolean stderrComplete = totalStderrLines <= MAX_STDERR_LINES;

            // Take last N lines (tail)
            List<String> truncatedStdout = stdoutComplete ? stdoutLines : stdoutLines.subList(totalStdoutLines - MAX_STDOUT_LINES, totalStdoutLines);
            List<String> truncatedStderr = stderrComplete ? stderrLines : stderrLines.subList(totalStderrLines - MAX_STDERR_LINES, totalStderrLines);

            writeJsonField(writer, "stdout", joinLines(truncatedStdout), true, 8);
            writer.write("        \"stdoutComplete\": " + stdoutComplete + ",\n");
            writeJsonField(writer, "stderr", joinLines(truncatedStderr), true, 8);
            writer.write("        \"stderrComplete\": " + stderrComplete);
        } else {
            writer.write("        \"stdout\": \"\",\n");
            writer.write("        \"stdoutComplete\": true,\n");
            writer.write("        \"stderr\": \"\",\n");
            writer.write("        \"stderrComplete\": true");
        }

        writer.write("\n      },\n");
    }

    static String filterStackTrace(String stacktrace, @Nullable String testClassName) {
        if (stacktrace == null || stacktrace.isEmpty()) {
            return stacktrace;
        }

        String[] lines = stacktrace.split("\n");
        StringBuilder filtered = new StringBuilder();
        int frameCount = 0;

        for (String line : lines) {
            if (line.startsWith("\tat ") || line.startsWith("    at ") || line.startsWith("\t\tat ")) {
                // This is a stack frame
                frameCount++;
                if (frameCount > MAX_STACKTRACE_FRAMES) {
                    break;
                }
                filtered.append(line).append('\n');

                // Stop after we hit the test class frame
                if (testClassName != null && line.contains(testClassName)) {
                    break;
                }
            } else {
                // Exception message line or "Caused by" — always include
                filtered.append(line).append('\n');
            }
        }

        // Remove trailing newline
        if (filtered.length() > 0 && filtered.charAt(filtered.length() - 1) == '\n') {
            filtered.setLength(filtered.length() - 1);
        }

        return filtered.toString();
    }

    private static int countFrames(String stacktrace) {
        if (stacktrace == null || stacktrace.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String line : stacktrace.split("\n")) {
            if (line.startsWith("\tat ") || line.startsWith("    at ") || line.startsWith("\t\tat ")) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private static String buildTestFilter(TestDescriptor descriptor) {
        String className = descriptor.getClassName();
        if (className == null) {
            return null;
        }
        String name = descriptor.getName();
        if (name != null && !name.equals(className)) {
            return className + "." + name;
        }
        return className;
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line);
        }
        return sb.toString();
    }

    private static String toEventPath(TestDescriptor descriptor) {
        List<String> names = new ArrayList<>();
        TestDescriptor current = descriptor;
        while (current != null) {
            names.add(current.getDisplayName());
            current = current.getParent();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = names.size() - 1; i >= 0; i--) {
            if (i < names.size() - 1) {
                sb.append(" > ");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    // JSON writing helpers

    private static void writeJsonField(BufferedWriter writer, String key, @Nullable String value, boolean hasMore) throws IOException {
        writeJsonField(writer, key, value, hasMore, 6);
    }

    private static void writeJsonField(BufferedWriter writer, String key, @Nullable String value, boolean hasMore, int indent) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
        String indentStr = sb.toString();

        writer.write(indentStr);
        writer.write("\"");
        writer.write(key);
        writer.write("\": ");
        if (value == null) {
            writer.write("null");
        } else {
            writer.write("\"");
            writer.write(escapeJson(value));
            writer.write("\"");
        }
        if (hasMore) {
            writer.write(",");
        }
        writer.write("\n");
    }

    static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    private static class FailureEntry {
        final TestDescriptorInternal descriptor;
        final TestFailure failure;
        @Nullable
        final OutputBuffer outputBuffer;

        FailureEntry(TestDescriptorInternal descriptor, TestFailure failure, @Nullable OutputBuffer outputBuffer) {
            this.descriptor = descriptor;
            this.failure = failure;
            this.outputBuffer = outputBuffer;
        }
    }

    private static class OutputBuffer {
        private final List<String> stdoutLines = new LinkedList<>();
        private final List<String> stderrLines = new LinkedList<>();

        void add(TestOutputEvent event) {
            if (event.getDestination() == TestOutputEvent.Destination.StdOut) {
                stdoutLines.add(event.getMessage());
            } else {
                stderrLines.add(event.getMessage());
            }
        }

        List<String> getStdoutLines() {
            return stdoutLines;
        }

        List<String> getStderrLines() {
            return stderrLines;
        }
    }
}
