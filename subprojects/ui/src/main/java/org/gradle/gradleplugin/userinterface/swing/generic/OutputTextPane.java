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
package org.gradle.gradleplugin.userinterface.swing.generic;

import org.gradle.foundation.output.FileLinkDefinitionLord;
import org.gradle.foundation.output.LiveOutputParser;
import org.gradle.foundation.output.FileLink;

import javax.swing.*;
import javax.swing.text.*;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Iterator;
import java.io.File;

/**
 * Rich text pane meant to simplify adding text, scrolling, prevent line wrapping, and highlighting FileLinks.
 */
public class OutputTextPane {
    private JScrollPane scroll;
    private final TextPane textPane;
    private DefaultStyledDocument document;

    private Font font;

    private AttributeSet defaultStyle;  //the style of most text
    private AttributeSet fileStyle;     //the style of file links

    private LiveOutputParser liveOutputParser;

    private Interaction interaction;
    private boolean allowsClickingFiles;  //determines whether or not we allow the user to click on file links. We'll highlight them if we allow this.
    private boolean hasClickableLinks;     //whether or not any clickable links actually exist

    private JPopupMenu popupMenu;

    /**
     * This allows us to interact with our parent control.
     */
    public interface Interaction {
        /**
         * Notification that the user clicked a file link
         *
         * @param file the file that was clicked
         * @param line the line number the file link points to. Will be -1 if no line was specified
         */
        public void fileClicked(File file, int line);
    }

    public OutputTextPane(Interaction interaction, boolean allowsClickingFiles, Font font, FileLinkDefinitionLord fileLinkDefinitionLord) {
        this.interaction = interaction;
        this.allowsClickingFiles = allowsClickingFiles;
        this.font = font;

        document = new DefaultStyledDocument();
        textPane = new TextPane(document);
        textPane.setEditable(false);
        textPane.setAutoscrolls(false);

        scroll = new JScrollPane(textPane);
        scroll.setAutoscrolls(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        //we have to set a new caret so we can force it not to scroll. We want to control the scrolling.
        //this is so a user can scroll up to look at the constantly updating output and not have it continue
        //scrolling. It also allows them to select something while the output is being updated. Without
        //this, their selection would be removed with each update.
        DefaultCaret caret = new DefaultCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        textPane.setCaret(caret);

        Color background = Color.white;
        textPane.setBackground(background);  //its not editable, but it looks better with a white background. (I tried using UI.Manager.getColor( "TextArea.background" ) (and others) but it was showing up as gray when using inside Idea. I think the L&F remapped some things and we want it white.
        scroll.setBackground(background);    //the scroll pane was showing up as grey in the Idea plugin. Not sure why. This should fix it.
        scroll.getViewport().setBackground(background);    //this makes the non-text area of the scroll pane appear white on Windows (if you have short text).
        resetFontStyles();

        textPane.addMouseListener(new MouseAdapter() {
            /**
             {@inheritDoc}
             */
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getButton() == MouseEvent.BUTTON3, e.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    showPopup(e.getPoint());
                }
            }
        });
        textPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e.getKeyCode());
            }
        });
        liveOutputParser = new LiveOutputParser(fileLinkDefinitionLord, true);
    }

    private void resetFontStyles() {
        defaultStyle = createDefaultAttributeSet();

        //setup the fileStyle
        StyleContext styleContent = StyleContext.getDefaultStyleContext();

        //modify the default to have a blue color and an underline
        fileStyle = createDefaultAttributeSet();
        fileStyle = styleContent.addAttribute(fileStyle, StyleConstants.Foreground, Color.blue);
        fileStyle = styleContent.addAttribute(fileStyle, StyleConstants.Underline, true);
    }

    /**
     * This creates a standard attribute set for the current text pane's font.
     *
     * @return an attribute set
     */
    private AttributeSet createDefaultAttributeSet() {
        StyleContext styleContent = StyleContext.getDefaultStyleContext();
        AttributeSet attributeSet = styleContent.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.FontFamily, font.getName());
        attributeSet = styleContent.addAttribute(attributeSet, StyleConstants.FontSize, font.getSize());
        return attributeSet;
    }

    public AttributeSet getDefaultStyle() {
        return defaultStyle;
    }

    /**
     * @return the component you would use to insert this control into a container. Its actually the scroll pane.
     */
    public JComponent asComponent() {
        return scroll;
    }

    public JTextPane getTextComponent() {
        return textPane;
    }

    public String getText() {
        return textPane.getText();
    }

    /**
     * When a user clicks, we determine if a FileLink was clicked on and if so, notify our interaction.
     */
    private void handleClick(boolean isRightButton, Point point) {
        if (!isRightButton && allowsClickingFiles) {
            FileLink fileLink = getFileLinkAt(point);
            if (fileLink != null) {
                interaction.fileClicked(fileLink.getFile(), fileLink.getLineNumber());
            }
        }
    }

    /**
     * When a user hits enter we'll open any file links that they're on. This is useful if the user is going to the next/previous link and want to open the current link.
     */
    private void handleKeyPress(int keyCode) {
        if (allowsClickingFiles) {
            if (keyCode == KeyEvent.VK_ENTER) {
                int caretLocation = textPane.getCaretPosition();
                if (caretLocation != -1) {
                    FileLink fileLink = liveOutputParser.getFileLink(caretLocation);
                    if (fileLink != null) {
                        interaction.fileClicked(fileLink.getFile(), fileLink.getLineNumber());
                    }
                }
            }
        }
    }

    private void showPopup(Point point) {
        buildPopup();

        popupMenu.show(textPane, point.x, point.y);
    }

    private void buildPopup() {
        if (popupMenu != null) {
            return;
        }

        popupMenu = new JPopupMenu();

        popupMenu.add(new AbstractAction("Copy") {
            public void actionPerformed(ActionEvent e) {
                String text = textPane.getSelectedText();
                if (text != null) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                }
            }
        });

        popupMenu.add(new AbstractAction("Select All") {
            public void actionPerformed(ActionEvent e) {
                textPane.selectAll();
            }
        });
    }

    /**
     * This appends the text to gradle's output window.
     */
    public void appendText(String text) {
        appendText(text, false);
    }


    /**
     * This sets the full text of this control, removing existing text.
     *
     * @param text the new text of this control
     */
    public void setText(String text) {
        liveOutputParser.reset();
        appendText(text, true);
    }

    /**
     * This appends or replaces the text to gradle's output window. This should be simple, but we've got a complication: We want to scroll nicely. By default, adding text doesn't scroll at all. We want
     * to scroll so the user can see the latest output. However, if the user scrolls manually to see older output, we don't want to keep scrolling to the end on them. This behavior is surprisingly
     * complicated to achieve. We have to determine if we're at the end of the viewport. If we are, we can scroll. However, we have to perform the actual scroll in an invokeLater because the text
     * control's size hasn't been updated yet. Also, this is where we parse the text looking for FileLinks
     *
     * @param text the text to add
     * @param replaceExisting true to replace the existing text completely, false to just append to the end.
     */
    private void appendText(String text, boolean replaceExisting) {

        Rectangle viewBounds = scroll.getViewport().getViewRect();  //the bounds of what we can see
        Dimension viewSize = scroll.getViewport().getViewSize();    //the total bounds of the text

        int maxViewBoundsY = viewBounds.y + viewBounds.height;

        //if they're close to the end, we should scroll. I could have said if viewSize.height == maxViewBoundsY
        //but this allows the user to scroll close to the end and get the same results
        boolean shouldScroll = viewSize.height - maxViewBoundsY < 20;

        try {
            if (replaceExisting)   //if we're supposed to be replacing, then do so.
            {
                hasClickableLinks = false;
                document.remove(0, document.getLength());
            }

            document.insertString(document.getLength(), text, defaultStyle);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        //parse this text and apply the styles accordingly. Note: the LiveOutputParser only returns FileLinks for full lines. The text
        //we add may contain a FileLink, but it won't parse it until it reaches a new line.
        if (allowsClickingFiles) {
            List<FileLink> fileLinks = liveOutputParser.appendText(text);
            highlightFileLinks(fileLinks);
        }

        if (shouldScroll) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    scrollToBottom();
                }
            });
        }
    }

    /**
     * This applies a text style to the text where the FileLinks are. This makes them appear to be clickable hotspots.
     *
     * @param fileLinks the FileLinks to apply
     */
    private void highlightFileLinks(List<FileLink> fileLinks) {
        Iterator<FileLink> iterator = fileLinks.iterator();
        while (iterator.hasNext()) {
            FileLink fileLink = iterator.next();

            document.setCharacterAttributes(fileLink.getStartingIndex(), fileLink.getLength(), fileStyle, false);
            hasClickableLinks = true;
        }
    }

    /**
     * This scrolls to the bottom of the output text. To do this, we can't just set the scroll bar to the maximum position. That would be too easy. We have to consider the height of the scrollbar
     * 'thumb'. This way also preserves whatever horizontal scrolling the user has done.
     */
    private void scrollToBottom() {
        int height = scroll.getVerticalScrollBar().getHeight();
        int maximum = scroll.getVerticalScrollBar().getMaximum();
        int newValue = maximum - height;
        scroll.getVerticalScrollBar().setValue(newValue);
    }

    /**
     * Returns the FileLink at the specified point.
     *
     * @param point the point where a FileLink may or may not be
     * @return a FileLink object if one exists at the specified point, null otherwise.
     */
    public FileLink getFileLinkAt(Point point) {
        int index = textPane.viewToModel(point);
        return liveOutputParser.getFileLink(index);
    }

    /**
     * This is only overridden to prevent line wrapping
     */
    private class TextPane extends JTextPane {

        private TextPane(DefaultStyledDocument doc) {
            super(doc);
        }

        /**
         * Overridden to prevent line wrapping
         *
         * @return always false
         */
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return false;
        }
    }

    public void setFont(Font font) {
        this.font = font;
        resetFontStyles();
        resetText();
    }

    /**
     * This resets the text using the current font styles. This is useful if you change fonts. The simplest way to do this is just re-add our text.
     */
    private void resetText() {
        String text = liveOutputParser.getText();
        liveOutputParser.reset();
        appendText(text, true);
    }

    public boolean hasClickableLinks() {
        return hasClickableLinks;
    }

    public boolean allowsClickingFiles() {
        return allowsClickingFiles;
    }

    /**
     * Selects and scrolls to the specified file link
     */
    public void selectFileLink(FileLink fileLink) {

        if (fileLink == null) {
            return;
        }

        textPane.setCaretPosition(fileLink.getStartingIndex());
        textPane.select(fileLink.getStartingIndex(), fileLink.getEndingIndex());

        try {
            Rectangle startingRectangle = textPane.modelToView(fileLink.getStartingIndex());
            Rectangle endDingRectangle = textPane.modelToView(fileLink.getEndingIndex());

            Rectangle totalBounds = startingRectangle.union(endDingRectangle);

            textPane.scrollRectToVisible(totalBounds);
            textPane.requestFocus();   //this seems to help the selection being painted
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the previous file link relative to the specified file link. If you pass in null, we'll return the last one.
     */
    public FileLink getPreviousFileLink() {
        return liveOutputParser.getPreviousFileLink(textPane.getCaretPosition());
    }

    /**
     * Returns the next file link relative to the specified file link. If you pass in null, we'll return the first one.
     */
    public FileLink getNextFileLink() {
        return liveOutputParser.getNextFileLink(textPane.getCaretPosition());
    }

    /**
     * Call this if you've changed any styles of our text and want to revert it back to the defaults.
     */
    public void resetHighlights() {
        document.setCharacterAttributes(0, document.getLength(), defaultStyle, true);

        if (allowsClickingFiles) {
            List<FileLink> fileLinks = liveOutputParser.getFileLinks();
            highlightFileLinks(fileLinks);
        }
    }
}
