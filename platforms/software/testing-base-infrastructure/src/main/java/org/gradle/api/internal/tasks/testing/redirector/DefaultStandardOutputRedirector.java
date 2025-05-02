/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.redirector;

import org.gradle.internal.io.LinePerThreadBufferingOutputStream;
import org.gradle.internal.io.TextStream;
import org.jspecify.annotations.Nullable;

import java.io.PrintStream;

public class DefaultStandardOutputRedirector implements StandardOutputRedirector {
    private PrintStream originalStdOut;
    private PrintStream originalStdErr;
    private final WriteAction stdOut = new WriteAction();
    private final WriteAction stdErr = new WriteAction();
    private final PrintStream redirectedStdOut = new LinePerThreadBufferingOutputStream(stdOut);
    private final PrintStream redirectedStdErr = new LinePerThreadBufferingOutputStream(stdErr);

    @Override
    public void redirectStandardOutputTo(OutputListener stdOutDestination) {
        stdOut.setDestination(stdOutDestination);
    }

    @Override
    public void redirectStandardErrorTo(OutputListener stdErrDestination) {
        stdErr.setDestination(stdErrDestination);
    }

    @Override
    public void start() {
        if (stdOut.destination != null) {
            originalStdOut = System.out;
            System.setOut(redirectedStdOut);
        }
        if (stdErr.destination != null) {
            originalStdErr = System.err;
            System.setErr(redirectedStdErr);
        }
    }

    @Override
    public void stop() {
        try {
            if (originalStdOut != null) {
                System.setOut(originalStdOut);
            }
            if (originalStdErr != null) {
                System.setErr(originalStdErr);
            }
            redirectedStdOut.flush();
            redirectedStdErr.flush();
        } finally {
            originalStdOut = null;
            originalStdErr = null;
            stdOut.setDestination(new DiscardAction());
            stdErr.setDestination(new DiscardAction());
        }
    }

    private static class DiscardAction implements OutputListener {
        @Override
        public void onOutput(CharSequence output) {
        }
    }

    private static class WriteAction implements TextStream {
        private OutputListener destination;

        @Override
        public void text(String message) {
            destination.onOutput(message);
        }

        @Override
        public void endOfStream(@Nullable Throwable failure) {
        }

        public void setDestination(OutputListener destination) {
            this.destination = destination;
        }
    }
}
