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
package org.gradle.internal.logging.console;

import org.gradle.internal.logging.text.Span;
import org.gradle.internal.logging.text.TestStyledTextOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsoleStub implements Console {
    private final TestableBuildOutputTextArea buildOutputArea = new TestableBuildOutputTextArea();
    private final TestableRedrawableLabel buildStatusLabel = new TestableRedrawableLabel("0");
    private final TestableBuildProgressTextArea buildProgressArea = new TestableBuildProgressTextArea(4);

    @Override
    public StyledLabel getStatusBar() {
        return buildStatusLabel;
    }

    @Override
    public BuildProgressArea getBuildProgressArea() {
        return buildProgressArea;
    }

    @Override
    public TextArea getBuildOutputArea() {
        return buildOutputArea;
    }

    @Override
    public void flush() {
        buildStatusLabel.redraw(null);
        buildProgressArea.redraw();
    }

    protected class TestableBuildOutputTextArea extends TestStyledTextOutput implements TextArea {
    }

    protected class TestableRedrawableLabel implements RedrawableLabel {
        String id; // Allows individual identification for debugging
        String display = "";
        String buffer;

        public TestableRedrawableLabel(String id) {
            this.id = id;
        }

        @Override
        public void setText(String text) {
            buffer = text;
        }

        @Override
        public void redraw(AnsiContext ansi) {
            display = buffer;
            buffer = null;
        }

        @Override
        public void setText(List<Span> spans) {
            buffer = "";
            for (Span span : spans) {
                buffer += span.getText();
            }
        }

        @Override
        public void setText(Span... spans) {
            setText(Arrays.asList(spans));
        }

        public String getDisplay() {
            return display;
        }
    }

    protected class TestableBuildProgressTextArea extends TestStyledTextOutput implements BuildProgressArea {
        boolean visible;
        private final List<TestableRedrawableLabel> testableLabels;
        private final List<StyledLabel> buildProgressLabels;

        public TestableBuildProgressTextArea(int numLabels) {
            this.buildProgressLabels = new ArrayList<StyledLabel>(numLabels);
            this.testableLabels = new ArrayList<TestableRedrawableLabel>(numLabels);

            for (int i = 0; i < numLabels; i++) {
                final TestableRedrawableLabel label = new TestableRedrawableLabel(String.valueOf(i + 1));
                buildProgressLabels.add(label);
                testableLabels.add(label);
            }
        }

        @Override
        public StyledLabel getProgressBar() {
            return buildStatusLabel;
        }

        @Override
        public List<StyledLabel> getBuildProgressLabels() {
            return buildProgressLabels;
        }

        @Override
        public void setVisible(boolean isVisible) {
            visible = isVisible;
        }

        public boolean getVisible() {
            return visible;
        }

        public List<String> getDisplay() {
            List<String> display = new ArrayList<String>(testableLabels.size());
            for (TestableRedrawableLabel label : testableLabels) {
                display.add(label.getDisplay());
            }
            return display;
        }

        void redraw() {
            for (TestableRedrawableLabel label : testableLabels) {
                label.redraw(null);
            }
        }
    }
}
