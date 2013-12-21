/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.gradleplugin.userinterface.swing.common;

import javax.swing.*;
import javax.swing.plaf.metal.MetalButtonUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

/**
 * This is button that has no border and only an image. It highlights when the user moves over it. This style was modeled after Idea. This was used because the borders on toolbars can get a little
 * busy and this looks a little cleaner.
 */
public class BorderlessImageButton extends JButton {
    private Color oldBackgroundColor;

    public BorderlessImageButton(Action action, Icon icon) {
        super(action);
        setUI();

        // If icon exist use icon otherwise let button use text ( if available ?).
        if (action.getValue(Action.SMALL_ICON) != null) {
            setText(null);
        }

        String name = (String) action.getValue(action.NAME);
        InputMap inputMap = this.getInputMap(this.WHEN_IN_FOCUSED_WINDOW);
        KeyStroke keyStroke = (KeyStroke) action.getValue(action.ACCELERATOR_KEY);
        inputMap.put(keyStroke, name);

        init(icon);
    }

    private void setUI() {
        // This fixes an issue where the WindowsButtonUI wants to draw a border
        // around a button that isn't in a toolbar.  This occurs even if you set
        // an empty border because it ignores your border and draws its own.
        setUI(MetalButtonUI.createUI(this));
    }

    private void init(Icon icon) {
        setBorder(BorderlessUtility.DEFAULT_BORDER);
        addMouseListener(new HighlightMouseListener());

        setText(null);

        if (icon != null) {
            setIcon(icon);

            int height = icon.getIconHeight();
            int width = icon.getIconWidth();
            Dimension preferredSize = new Dimension(width + 2, height + 2); //plus 2 for the border

            setMinimumSize(preferredSize);
            setMaximumSize(preferredSize);
            setPreferredSize(preferredSize);
            setFocusPainted(false);
        }
    }

    private class HighlightMouseListener extends MouseAdapter {
        private HighlightMouseListener() {
        }

        public void mouseEntered(MouseEvent event) {
            if (getAction() != null ? getAction().isEnabled() : isEnabled()) {
                oldBackgroundColor = BorderlessImageButton.this.getBackground();
                BorderlessImageButton.this.setBackground(BorderlessUtility.ON_MOUSE_OVER_BACKGROUND);
                BorderlessImageButton.this.setBorder(BorderlessUtility.ON_MOUSEOVER_BORDER);
            }
        }

        public void mousePressed(MouseEvent event) {
            if (getAction() != null ? getAction().isEnabled() : isEnabled()) {
                BorderlessImageButton.this.setBackground(BorderlessUtility.ON_BUTTON_PRESSED_BACKGROUND);
            }
        }

        public void mouseReleased(MouseEvent event) {
            if (getAction() != null ? getAction().isEnabled() : isEnabled()) {
                // do a hit test to make sure the mouse is being released inside the button
                Rectangle2D buttonRect = BorderlessImageButton.this.getBounds();
                if (buttonRect.contains(event.getPoint())) {
                    BorderlessImageButton.this.setBackground(BorderlessUtility.ON_MOUSE_OVER_BACKGROUND);
                }
            }
        }

        public void mouseExited(MouseEvent event) {
            BorderlessImageButton.this.setBackground(oldBackgroundColor);
            BorderlessImageButton.this.setBorder(BorderlessUtility.DEFAULT_BORDER);
        }
    }
}
