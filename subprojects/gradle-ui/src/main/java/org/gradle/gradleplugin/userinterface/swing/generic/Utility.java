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

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JButton;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.AbstractButton;
import javax.imageio.ImageIO;
import java.awt.Component;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.io.IOException;

/**
 * Just some utility functions.
 *
 * @author mhunsicker
 */
public class Utility {
    private static final Logger logger = Logging.getLogger(Utility.class);

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
       This creates a dialog. I only created this because I was using JDK 1.6,
       then realized I needed to use 1.5 and one of the most useful features of
       1.6 is dialogs taking Windows as parents. This abstracts that so I don't
       have to make major changes to the code by passing around JFrames and JDialogs
       explicitly.

       @param  parent     the parent window
       @param  isModal    true if its modal, false if not.
       @return a dialog
    */
    public static JDialog createDialog(Window parent, String title, boolean isModal) {
        if (parent instanceof JDialog)
            return new JDialog((JDialog) parent, title, isModal);
        else if (parent instanceof JFrame)
            return new JDialog((JFrame) parent, title, isModal);

        throw new RuntimeException("Unknown window type!");
    }

    /**
       This uses reflection to set the tab component if we're running under 1.6.
       It does nothing if you're running under 1.5. This is so you can run this
       on java 1.6 and get this benefit, but its not required to compile.

       This is the same as calling JTabbedPane.setTabComponentAt(). It just does
       so using reflection.
    */
    public static void setTabComponent15Compatible(JTabbedPane tabbedPane, int index, Component component) {
        try {
            Method method = tabbedPane.getClass().getMethod("setTabComponentAt", new Class[]{Integer.TYPE, Component.class});
            method.invoke(tabbedPane, index, component);
        }
        catch (NoSuchMethodException e) {
            //e.printStackTrace();
            //we're not requiring 1.6, so its not a problem if we don't find the method. We just don't get this feature.
        }
        catch (Exception e) {
            logger.error("Setting tab component", e);
        }
    }

    /**
       This creates a button with the specified action, image, and tooltip text.
       The main issue here is that it doesn't crash if the image is missing
       (which is just something that happens in real life from time to time).
       You probably should specify a name on the action just in case.

       @param  imageResourceName the image resource
       @param  tooltip           the tooltip to display
       @param  action            the action to perform
       @return the button that was created.
    */
    public static JButton createButton(Class resourceClass, String imageResourceName, String tooltip, Action action) {
        JButton button = new JButton(action);

        setupButton(button, resourceClass, imageResourceName, tooltip);

        return button;
    }

    /**
       This sets up a button's image and tooltip text. The main issue here is
       that it doesn't crash if the image is missing (which is just something
       that happens in real life from time to time). You probably should specify
       a name on the action just in case.

       @param  button            the button to affect
       @param resourceClass
       @param  imageResourceName the image resource
       @param  tooltip           the tooltip to display
       @author mhunsicker
    */
    public static void setupButton(AbstractButton button, Class resourceClass, String imageResourceName, String tooltip) {
        if (imageResourceName != null) {
            InputStream inputStream = resourceClass.getResourceAsStream(imageResourceName);
            if (inputStream != null) {
                try {
                    BufferedImage image = ImageIO.read(inputStream);
                    button.setIcon(new ImageIcon(image));
                    button.setText(null);
                }
                catch (IOException e) {
                    logger.error("Reading image " + imageResourceName, e);
                }
            }
        }

        if (tooltip != null)
            button.setToolTipText(tooltip);
    }
}
