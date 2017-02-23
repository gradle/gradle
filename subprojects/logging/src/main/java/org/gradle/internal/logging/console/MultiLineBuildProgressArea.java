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

import org.gradle.api.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiLineBuildProgressArea implements BuildProgressArea {
    private final List<DefaultRedrawableLabel> entries;
    private final DefaultRedrawableLabel progressBarLabel;

    private final List<StyledLabel> buildProgressLabels;
    private final Cursor statusAreaPos = new Cursor();
    private boolean isVisible;

    public MultiLineBuildProgressArea(int numLabels) {
        this.buildProgressLabels = new ArrayList<StyledLabel>(numLabels);
        // 2 extra lines: 1 for BuildStatus and 1 for Cursor parking space
        this.entries = new ArrayList<DefaultRedrawableLabel>(numLabels + 2);

        int row = 0;

        progressBarLabel = newLabel(row--);
        entries.add(progressBarLabel);

        for (int i = 0; i < numLabels; ++i) {
            DefaultRedrawableLabel label = newLabel(row--);
            entries.add(label);
            buildProgressLabels.add(label);
        }

        // Parking space for the write cursor
        entries.add(newLabel(row--));
    }

    private DefaultRedrawableLabel newLabel(int row) {
        return new DefaultRedrawableLabel(Cursor.at(row--, 0));
    }

    @Override
    public List<StyledLabel> getBuildProgressLabels() {
        return Collections.unmodifiableList(buildProgressLabels);
    }

    @Override
    public StyledLabel getProgressBar() {
        return progressBarLabel;
    }

    public Cursor getWritePosition() {
        return statusAreaPos;
    }

    public int getHeight() {
        return entries.size();
    }

    @Override
    public void setVisible(boolean isVisible) {
        this.isVisible = isVisible;
        for (DefaultRedrawableLabel label : entries) {
            label.setVisible(isVisible);
        }
    }

    public boolean isOverlappingWith(Cursor cursor) {
        for (DefaultRedrawableLabel label : entries) {
            if (label.isOverlappingWith(cursor)) {
                return true;
            }
        }
        return false;
    }

    public void newLineAdjustment() {
        statusAreaPos.row++;
        for (DefaultRedrawableLabel label : entries) {
            label.newLineAdjustment();
        }
    }

    public void redraw(AnsiContext ansi) {
        int newLines = 0 - statusAreaPos.row + getHeight() - 1;
        if (isVisible && newLines > 0) {
            ansi.cursorAt(Cursor.newBottomLeft()).newLines(newLines);
        }

        // Redraw every entries of this area
        for (int i = 0; i < entries.size(); ++i) {
            DefaultRedrawableLabel label = entries.get(i);

            label.redraw(ansi);

            // Ensure a clean end of the line when the area scrolls
            if (isVisible && newLines > 0 && (i + newLines) < entries.size()) {
                int currentLength = label.getWritePosition().col;
                int previousLength = entries.get(i + newLines).getWritePosition().col;
                if (currentLength < previousLength) {
                    ansi.writeAt(label.getWritePosition()).eraseForward();
                }
            }
        }

        ansi.cursorAt(parkCursor());
    }

    // According to absolute positioning
    public void scrollBy(int rows) {
        statusAreaPos.row -= rows;
        for (DefaultRedrawableLabel label : entries) {
            label.scrollBy(rows);
        }
    }

    // According to absolute positioning
    public void scrollUpBy(int rows) {
        scrollBy(-rows);
    }

    // According to absolute positioning
    public void scrollDownBy(int rows) {
        scrollBy(rows);
    }

    private Cursor parkCursor() {
        if (isVisible) {
            return Cursor.newBottomLeft();
        } else {
            return statusAreaPos;
        }
    }
}
