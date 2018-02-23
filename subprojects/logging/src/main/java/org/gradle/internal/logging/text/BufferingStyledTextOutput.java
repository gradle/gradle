/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.text;

import org.gradle.api.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link StyledTextOutput} which buffers the content written to it, for later forwarding to another {@link StyledTextOutput} instance.
 */
public class BufferingStyledTextOutput extends AbstractStyledTextOutput {
    private final List<Action<StyledTextOutput>> events = new ArrayList<Action<StyledTextOutput>>();
    private boolean hasContent;

    /**
     * Writes the buffered contents of this output to the given target, and clears the buffer.
     */
    public void writeTo(StyledTextOutput output) {
        for (Action<StyledTextOutput> event : events) {
            event.execute(output);
        }
        events.clear();
    }

    @Override
    protected void doStyleChange(final StyledTextOutput.Style style) {
        if (!events.isEmpty() && (events.get(events.size() - 1) instanceof ChangeStyleAction)) {
            events.remove(events.size() - 1);
        }
        events.add(new ChangeStyleAction(style));
    }

    @Override
    protected void doAppend(final String text) {
        if (text.length() == 0) {
            return;
        }
        hasContent = true;
        events.add(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                styledTextOutput.text(text);
            }
        });
    }

    public boolean getHasContent() {
        return hasContent;
    }

    private static class ChangeStyleAction implements Action<StyledTextOutput> {
        private final StyledTextOutput.Style style;

        public ChangeStyleAction(StyledTextOutput.Style style) {
            this.style = style;
        }

        public void execute(StyledTextOutput styledTextOutput) {
            styledTextOutput.style(style);
        }
    }
}
