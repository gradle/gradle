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
import org.gradle.gradleplugin.userinterface.swing.common.BorderlessImageButton;
import org.gradle.gradleplugin.userinterface.swing.common.BorderlessImageToggleButton;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

/**
 * Just some utility functions.
 */
public class Utility {
    private static final Logger LOGGER = Logging.getLogger(Utility.class);

    public static Component addLeftJustifiedComponent(Component component) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        panel.add(component);
        panel.add(Box.createHorizontalGlue());

        return panel;
    }

    public static Component addRightJustifiedComponent(Component component) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        panel.add(Box.createHorizontalGlue());
        panel.add(component);

        return panel;
    }

    /**
     * This creates a dialog. I only created this because I was using JDK 1.6, then realized I needed to use 1.5 and one of the most useful features of 1.6 is dialogs taking Windows as parents. This
     * abstracts that so I don't have to make major changes to the code by passing around JFrames and JDialogs explicitly.
     *
     * @param parent the parent window
     * @param isModal true if its modal, false if not.
     * @return a dialog
     */
    public static JDialog createDialog(Window parent, String title, boolean isModal) {
        if (parent instanceof JDialog) {
            return new JDialog((JDialog) parent, title, isModal);
        } else if (parent instanceof JFrame) {
            return new JDialog((JFrame) parent, title, isModal);
        }

        throw new RuntimeException("Unknown window type!");
    }

    /**
     * This uses reflection to set the tab component if we're running under 1.6. It does nothing if you're running under 1.5. This is so you can run this on java 1.6 and get this benefit, but its not
     * required to compile.
     *
     * This is the same as calling JTabbedPane.setTabComponentAt(). It just does so using reflection.
     */
    public static void setTabComponent15Compatible(JTabbedPane tabbedPane, int index, Component component) {
        try {
            Method method = tabbedPane.getClass().getMethod("setTabComponentAt", new Class[]{Integer.TYPE, Component.class});
            method.invoke(tabbedPane, index, component);
        } catch (NoSuchMethodException e) {
            //e.printStackTrace();
            //we're not requiring 1.6, so its not a problem if we don't find the method. We just don't get this feature.
        } catch (Exception e) {
            LOGGER.error("Setting tab component", e);
        }
    }

    /**
     * This creates a button with the specified action, image, and tooltip text. The main issue here is that it doesn't crash if the image is missing (which is just something that happens in real life
     * from time to time). You probably should specify a name on the action just in case.
     *
     * @param resourceClass the calling class. Useful when multiple classloaders are used.
     * @param imageResourceName the image resource
     * @param tooltip the tooltip to display
     * @param action the action to perform
     * @return the button that was created.
     */
    public static JButton createButton(Class resourceClass, String imageResourceName, String tooltip, Action action) {

        JButton button = null;
        if (imageResourceName != null) {
            InputStream inputStream = resourceClass.getResourceAsStream(imageResourceName);
            if (inputStream != null) {
                try {
                    BufferedImage image = ImageIO.read(inputStream);

                    button = new BorderlessImageButton(action, new ImageIcon(image));
                } catch (IOException e) {
                    LOGGER.error("Reading image " + imageResourceName, e);
                }
            }
        }

        if (button == null) {
            button = new JButton(action);
        }

        if (tooltip != null) {
            button.setToolTipText(tooltip);
        }

        return button;
    }

    public static JToggleButton createToggleButton(Class resourceClass, String imageResourceName, String tooltip, Action action) {

        JToggleButton button = null;

        if (imageResourceName != null) {
            ImageIcon icon = getImageIcon(resourceClass, imageResourceName);
            if (icon != null) {
                button = new BorderlessImageToggleButton(action, icon);
            }
        }

        if (button == null) {
            button = new JToggleButton(action);
        }

        if (tooltip != null) {
            button.setToolTipText(tooltip);
        }

        return button;
    }

    public static JMenuItem createMenuItem(Class resourceClass, String name, String imageResourceName, Action action) {
        JMenuItem item = new JMenuItem(action);
        item.setText(name);

        if (imageResourceName != null) {
            ImageIcon icon = getImageIcon(resourceClass, imageResourceName);
            item.setIcon(icon);
        }

        return item;
    }

    /**
     * this determines if the CTRL key is down based on the modifiers from an event. This actualy needs to be checking for different things. CTRL doesn't meant the same thing on each platform.
     */
    public static boolean isCTRLDown(int eventModifiersEx) {
        return (eventModifiersEx & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK;
    }

    public static ImageIcon getImageIcon(Class resourceClass, String imageResourceName) {
        InputStream inputStream = resourceClass.getResourceAsStream(imageResourceName);
        if (inputStream != null) {
            try {
                BufferedImage image = ImageIO.read(inputStream);
                return new ImageIcon(image);
            } catch (IOException e) {
                LOGGER.error("Reading image " + imageResourceName, e);
            }
        }

        return null;
    }

    /**
     * Scrolls the specified text component so the text between the starting and ending index are visible.
     */
    public static void scrollToText(JTextComponent textComponent, int startingIndex, int endingIndex) {
        try {
            Rectangle startingRectangle = textComponent.modelToView(startingIndex);
            Rectangle endDingRectangle = textComponent.modelToView(endingIndex);

            Rectangle totalBounds = startingRectangle.union(endDingRectangle);

            textComponent.scrollRectToVisible(totalBounds);
            textComponent.repaint();
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
}
