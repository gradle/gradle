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
import javax.swing.border.Border;
import javax.swing.plaf.metal.MetalButtonUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

/**
 * This is button that has no border and only an image. It highlights when the user moves over it. This version is a toggle button. This style was modeled after Idea. This was used because the borders
 * on toolbars can get a little busy and this looks a little cleaner.
 */
public class BorderlessImageToggleButton extends JToggleButton {
    public Border selectedBorder = BorderFactory.createLoweredBevelBorder();
    private Color defaultBackground;

    public BorderlessImageToggleButton(Action action, Icon icon) {
        super(action);
        setUI();

        this.init(icon);
    }

    private void setUI() {
        // This fixes an issue where the WindowsButtonUI wants to draw a border
        // around a button that isn't in a toolbar.  This occurs even if you set
        // an empty border because it ignores your border and draws its own.
        setUI(MetalButtonUI.createUI(this));
    }

    private void init(Icon icon) {
        this.setBorder(BorderlessUtility.DEFAULT_BORDER);
        defaultBackground = this.getBackground();
        this.addMouseListener(new HighlightMouseListener());

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

    public void setSelected(boolean select) {
        super.setSelected(select);
        setBorder(null);
    }

    /**
     * I added this to correct an architecture problem. Whenever this button was removed or added to a parent container the underlying swing architecture was resetting the border and it wasn't taking
     * into account our need to change the border depending on the selection state of the button. This overrides negates that effect causing the button to behave as intended.
     *
     * @param border The new border to set for this button the we disregard and replace with our own.
     */
    public void setBorder(Border border) {
        super.setBorder(BorderlessImageToggleButton.this.isSelected() ? selectedBorder : BorderlessUtility.DEFAULT_BORDER);
    }

    private class HighlightMouseListener extends MouseAdapter {
        public HighlightMouseListener() {
        }

        public void mouseEntered(MouseEvent event) {
            if (getAction() != null ? getAction().isEnabled() : isEnabled()) {
                BorderlessImageToggleButton.this.setBackground(BorderlessUtility.ON_MOUSE_OVER_BACKGROUND);
                BorderlessImageToggleButton.this.setBorder(BorderlessUtility.ON_MOUSEOVER_BORDER);
            }
        }

        public void mousePressed(MouseEvent event) {
            if (getAction() != null ? getAction().isEnabled() : isEnabled()) {
                BorderlessImageToggleButton.this.setBackground(BorderlessUtility.ON_BUTTON_PRESSED_BACKGROUND);
            }
        }

        public void mouseReleased(MouseEvent event) {
            if (getAction() != null ? getAction().isEnabled() : isEnabled()) {
                // do a hit test to make sure the mouse is being released inside the button
                Rectangle2D buttonRect = BorderlessImageToggleButton.this.getBounds();
                if (buttonRect.contains(event.getPoint())) {
                    BorderlessImageToggleButton.this.setBackground(BorderlessUtility.ON_MOUSE_OVER_BACKGROUND);
                }
            }
        }

        public void mouseExited(MouseEvent event) {
            BorderlessImageToggleButton.this.setBackground(defaultBackground);

            if (BorderlessImageToggleButton.this.isSelected()) {
                BorderlessImageToggleButton.this.setBorder(selectedBorder);
            } else {
                BorderlessImageToggleButton.this.setBorder(BorderlessUtility.DEFAULT_BORDER);
            }
        }
    }
}
