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

package org.gradle.internal.logging.console;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/// Build state reporting to the terminal with OSC 7501 (Program Status Protocol) sequences.
///
/// @see <a href="https://www.superlogical.com/rex/docs/build/program-status">Program Status Protocol</a>
public class ProgramStatusReporter {

    /// Client JVM system property that disables the reports when set to `false`.
    public static final String ENABLED_PROPERTY = "org.gradle.internal.console.program-status";

    /// Limit of the protocol for the decoded `msg` value.
    static final int MAX_MESSAGE_BYTES = 2048;

    private static final String APP = "gradle";

    /// Program states reported by Gradle.
    enum State {
        IDLE("idle"),
        WORKING("working"),
        BLOCKED("blocked"),
        DONE("done"),
        ERROR("error");

        private final String value;

        State(String value) {
            this.value = value;
        }

        private boolean isOutcome() {
            return this == DONE || this == ERROR;
        }
    }

    private final OutputStream output;
    private final long clientStartTime;

    private @Nullable State buildState;
    private @Nullable Integer buildProgress;
    private @Nullable String buildMessage;
    private @Nullable String question;

    private @Nullable State lastState;
    private @Nullable String lastSequence;

    /// @param clientStartTime start of the client JVM, from which the duration of the first build printed on the console is measured
    public ProgramStatusReporter(OutputStream output, long clientStartTime) {
        this.output = output;
        this.clientStartTime = clientStartTime;
    }

    long getClientStartTime() {
        return clientStartTime;
    }

    /// Records that the build is running, without writing a report.
    ///
    /// @param progress percentage of completed work, or `null` when unknown
    synchronized void updateWorking(@Nullable Integer progress) {
        buildState = State.WORKING;
        buildProgress = progress;
        buildMessage = null;
    }

    /// Records the outcome of the build, without writing a report.
    synchronized void updateOutcome(boolean failed, String message) {
        buildState = failed ? State.ERROR : State.DONE;
        buildProgress = null;
        buildMessage = toMessage(message);
    }

    /// Writes a `blocked` report for a prompt that waits for an answer.
    synchronized void blocked(String prompt) {
        question = toMessage(prompt);
        flush();
    }

    /// Writes the state of the build again once a prompt has been answered.
    synchronized void unblocked() {
        question = null;
        flush();
    }

    /// Writes the recorded state unless it equals the last one written; call it only between whole console writes.
    synchronized void flush() {
        if (question != null) {
            write(State.BLOCKED, null, question);
        } else if (buildState != null) {
            write(buildState, buildProgress, buildMessage);
        }
    }

    /// Writes a known but unwritten outcome, or else replaces a `working` or `blocked` report with `idle`, and never overwrites a written outcome.
    public synchronized void exit() {
        if (buildState != null && buildState.isOutcome()) {
            write(buildState, null, buildMessage);
        } else if (lastState == State.WORKING || lastState == State.BLOCKED) {
            question = null;
            write(State.IDLE, null, null);
        }
    }

    private void write(State state, @Nullable Integer progress, @Nullable String message) {
        String sequence = buildSequence(state, progress, message);
        if (sequence.equals(lastSequence)) {
            return;
        }
        lastState = state;
        lastSequence = sequence;
        try {
            output.write(sequence.getBytes(StandardCharsets.US_ASCII));
            output.flush();
        } catch (IOException ignored) {
            // The terminal is gone, nothing to report to
        }
    }

    /// Returns `ESC ] 7501 ; state=<state>[:kind=question]:app=gradle[:progress=<0-100>][:msg=<base64>] ESC \`.
    static String buildSequence(State state, @Nullable Integer progress, @Nullable String message) {
        StringBuilder sequence = new StringBuilder("\u001B]7501;state=").append(state.value);
        if (state == State.BLOCKED) {
            sequence.append(":kind=question");
        }
        sequence.append(":app=").append(APP);
        if (state == State.WORKING && progress != null) {
            sequence.append(":progress=").append(Math.max(0, Math.min(100, progress)));
        }
        if (message != null && !message.isEmpty()) {
            sequence.append(":msg=").append(Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8)));
        }
        return sequence.append("\u001B\\").toString();
    }

    /// Turns text into a single line without control characters, at most [#MAX_MESSAGE_BYTES] long in UTF-8.
    static String toMessage(String text) {
        StringBuilder message = new StringBuilder();
        int bytes = 0;
        boolean pendingSpace = false;
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)) {
                pendingSpace = message.length() > 0;
                continue;
            }
            int length = (pendingSpace ? 1 : 0) + utf8Length(codePoint);
            if (bytes + length > MAX_MESSAGE_BYTES) {
                break;
            }
            if (pendingSpace) {
                message.append(' ');
                pendingSpace = false;
            }
            message.appendCodePoint(codePoint);
            bytes += length;
        }
        return message.toString();
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        } else if (codePoint < 0x800) {
            return 2;
        } else if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }
}
