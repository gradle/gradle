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
package org.gradle.gradleplugin.userinterface.swing.generic;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * This just wraps up an OutputPanel so it has a tab header that can be dynamic. The current (rather awkward) JTabbedPane implementation is to separate the tab contents from its component. This only
 * works with java 1.6 or later.
 */
public class OutputTab extends OutputPanel {

    private static final Logger LOGGER = Logging.getLogger(OutputTab.class);

    private JPanel mainPanel;
    private JLabel mainTextLabel;
    private JLabel pinnedLabel;
    private JLabel closeLabel;

    private static ImageIcon closeIcon;
    private static ImageIcon closeHighlightIcon;
    private static ImageIcon pinnedIcon;

    public OutputTab(GradlePluginLord gradlePluginLord, OutputPanelParent parent, String header, AlternateUIInteraction alternateUIInteraction) {
        super(gradlePluginLord, parent, alternateUIInteraction);
        mainPanel = new JPanel();
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));

        mainTextLabel = new JLabel(header);

        if (pinnedIcon == null) {
            pinnedIcon = getImageIconResource("/org/gradle/gradleplugin/userinterface/swing/generic/pin.png");
        }

        pinnedLabel = new JLabel(pinnedIcon);
        pinnedLabel.setVisible(isPinned());

        setupCloseLabel();

        mainPanel.add(mainTextLabel);
        mainPanel.add(Box.createHorizontalStrut(5));
        mainPanel.add(pinnedLabel);
        mainPanel.add(closeLabel);
    }

    private void setupCloseLabel() {
        if (closeIcon == null) {
            closeIcon = getImageIconResource("close.png");
            closeHighlightIcon = getImageIconResource("close-highlight.png");
        }

        closeLabel = new JLabel(closeIcon);
        closeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeLabel.setIcon(closeHighlightIcon);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeLabel.setIcon(closeIcon);
            }

            public void mouseClicked(MouseEvent e) {
                close();
            }
        });
    }

    private BufferedImage getImageResource(String imageResourceName) {
        InputStream inputStream = getClass().getResourceAsStream(imageResourceName);
        if (inputStream != null) {
            try {
                BufferedImage image = ImageIO.read(inputStream);
                return image;
            } catch (IOException e) {
                LOGGER.error("Reading image " + imageResourceName, e);
            }
        }

        return null;
    }

    private ImageIcon getImageIconResource(String imageIconResourceName) {
        BufferedImage image = getImageResource(imageIconResourceName);
        if (image != null) {
            return new ImageIcon(image);
        }
        return null;
    }

    /**
     * Call this before you use this tab. It resets its output as well as enabling buttons appropriately.
     *
     */
    @Override
    public void reset() {
        super.reset();
        closeLabel.setEnabled(true);
    }

    public Component getTabHeader() {
        return mainPanel;
    }

    public void setTabHeaderText(String newText) {
        mainTextLabel.setText(newText);
    }

    public boolean close() {
        closeLabel.setEnabled(false); // provide feedback to the user that we received their click

        boolean result = super.close();
        if (result) {
            closeLabel.setEnabled(true);
        }

        return result;
    }

    /**
     * Overridden so we can indicate the pinned state.
     *
     * @param pinned whether or not we're pinned
     */
    @Override
    public void setPinned(boolean pinned) {
        pinnedLabel.setVisible(pinned);

        super.setPinned(pinned);
    }
}
