/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.logging.internal;

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.logging.StyledTextOutput;
import org.gradle.util.LineBufferingOutputStream;
import org.gradle.util.TimeProvider;

import java.io.IOException;

public class LoggingBackedStyledTextOutput extends AbstractStyledTextOutput {
    private final LineBufferingOutputStream outstr;

    public LoggingBackedStyledTextOutput(OutputEventListener listener, String category, TimeProvider timeProvider) {
        outstr = new LineBufferingOutputStream(new LogAction(listener, category, timeProvider), true);
    }

    public StyledTextOutput text(Object text) {
        try {
            outstr.write(text.toString().getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    private static class LogAction implements Action<String> {
        private final OutputEventListener listener;
        private final String category;
        private final TimeProvider timeProvider;

        public LogAction(OutputEventListener listener, String category, TimeProvider timeProvider) {
            this.listener = listener;
            this.category = category;
            this.timeProvider = timeProvider;
        }

        public void execute(String text) {
            listener.onOutput(new StyledTextOutputEvent(timeProvider.getCurrentTime(), category, text));
        }
    }
}
