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

import org.gradle.api.logging.LogLevel;
import org.gradle.logging.StyledTextOutput;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.gradle.logging.StyledTextOutput.Style.Error;
import static org.gradle.logging.StyledTextOutput.Style.Normal;

public class StyledTextOutputBackedRenderer implements OutputEventListener {
    private final OutputEventTextOutputImpl textOutput;
    private boolean debugOutput;
    private RenderableOutputEvent lastEvent;

    public StyledTextOutputBackedRenderer(StyledTextOutput textOutput) {
        this.textOutput = new OutputEventTextOutputImpl(textOutput);
    }

    public void onOutput(OutputEvent event) {
        if (event instanceof LogLevelChangeEvent) {
            LogLevelChangeEvent changeEvent = (LogLevelChangeEvent) event;
            debugOutput = changeEvent.getNewLogLevel() == LogLevel.DEBUG;
        }
        if (event instanceof RenderableOutputEvent) {
            RenderableOutputEvent outputEvent = (RenderableOutputEvent) event;
            textOutput.style(outputEvent.getLogLevel() == LogLevel.ERROR ? Error : Normal);
            if (debugOutput && (textOutput.atEndOfLine || lastEvent == null || !lastEvent.getCategory().equals(outputEvent.getCategory()))) {
                if (!textOutput.atEndOfLine) {
                    textOutput.println();
                }
                textOutput.text(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(outputEvent.getTimestamp())));
                textOutput.text(" [");
                textOutput.text(outputEvent.getLogLevel());
                textOutput.text("] [");
                textOutput.text(outputEvent.getCategory());
                textOutput.text("] ");
            }
            outputEvent.render(textOutput);
            lastEvent = outputEvent;
            textOutput.style(Normal);
        }
    }

    private class OutputEventTextOutputImpl extends AbstractLineChoppingStyledTextOutput {
        private final StyledTextOutput textOutput;
        private boolean atEndOfLine = true;

        public OutputEventTextOutputImpl(StyledTextOutput textOutput) {
            this.textOutput = textOutput;
        }

        @Override
        protected void doStyleChange(Style style) {
            textOutput.style(style);
        }

        @Override
        protected void doLineText(CharSequence text, boolean terminatesLine) {
            textOutput.text(text);
            atEndOfLine = terminatesLine;
        }
    }
}
