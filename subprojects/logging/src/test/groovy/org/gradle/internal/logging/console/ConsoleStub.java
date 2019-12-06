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

import org.gradle.internal.logging.text.TestStyledTextOutput;

import java.util.ArrayList;
import java.util.List;

public class ConsoleStub implements Console {
    private final TestableBuildOutputTextArea buildOutputArea = new TestableBuildOutputTextArea();
    private final TestableRedrawableLabel buildStatusLabel = new TestableRedrawableLabel("0");
    private final TestableBuildProgressTextArea buildProgressArea = new TestableBuildProgressTextArea();

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

    protected static class TestableBuildOutputTextArea extends TestStyledTextOutput implements TextArea {
    }

    protected static class TestableRedrawableLabel extends TestStyledLabel implements RedrawableLabel {
        String id; // Allows individual identification for debugging
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
            if (buffer != null) {
                super.setDisplay(buffer);
                buffer = null;
            }
        }
    }

    protected class TestableBuildProgressTextArea extends TestStyledTextOutput implements BuildProgressArea {
        boolean visible;
        int buildProgressLabelCount;
        private final List<TestableRedrawableLabel> testableLabels = new ArrayList<TestableRedrawableLabel>();
        private final List<StyledLabel> buildProgressLabels = new ArrayList<StyledLabel>();

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

        @Override
        public void resizeBuildProgressTo(int buildProgressLabelCount) {
            for (int i = buildProgressLabelCount - this.buildProgressLabelCount; i > 0; --i) {
                final TestableRedrawableLabel label = new TestableRedrawableLabel(String.valueOf((buildProgressLabelCount - i) + 1));
                buildProgressLabels.add(label);
                testableLabels.add(label);
            }
            this.buildProgressLabelCount = buildProgressLabelCount;
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
