/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.gradleplugin.foundation.search.BasicTextSearchCriteria;
import org.gradle.gradleplugin.foundation.search.TextBlockSearchEditor;
import org.gradle.gradleplugin.userinterface.swing.generic.Utility;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A simple search panel (opposed to a dialog box). It is meant to be added to the bottom or top of a panel to search and hidden. It is then displayed when needed. However, you could keep it visible
 * if you choose.
 *
 * This performs a threaded search. Its not that the search will take a particularly long time (although it certainly could). Its that we're doing work and we can't do that in the Swing EDT. We'll be
 * searching every time a user types a letter, As such, we queue up our requests and then perform a search on the latest request.
 */
public class SearchPanel {
    private final Logger logger = Logging.getLogger(SearchPanel.class);

    private JPanel mainPanel;

    private JTextField textToMatchField;
    private JCheckBox isCaseSensitiveCheckBox;
    private JCheckBox useRegularExpressionsCheckBox;
    private JButton findNextButton;
    private JButton findPreviousButton;

    private SearchInteraction searchInteraction;

    private TextBlockSearchEditor editor = new TextBlockSearchEditor();

    private Color notFoundColor = Color.red.brighter();

    private volatile LinkedBlockingQueue<SearchRequest> searchRequests = new LinkedBlockingQueue<SearchRequest>();
    private ExecutorService executorService;

    //
    public interface SearchInteraction {
        /**
         * @return the block of text that we're going to search
         */
        public String getTextToSearch();

        /**
         * @return the current location of hte caret within the text we're going to search.
         */
        public int getCaretLocation();

        /**
         * Highlight and then ensure this result is visible.
         *
         * @param editor the editor that was used to search
         * @param searchResult the specific result (within the editor's search results) to highlight.
         */
        public void highlightAndScrollToResult(TextBlockSearchEditor editor, TextBlockSearchEditor.SearchResult searchResult);

        /**
         * Notification that the search was complete and we have results to show to the user.
         */
        public void searchComplete(TextBlockSearchEditor editor);

        /**
         * Notification to hide your results. This panel is going away or the user cleared the results.
         */
        public void removeResultHighlights();
    }

    public SearchPanel(SearchInteraction searchInteraction) {
        this.searchInteraction = searchInteraction;

        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(new SearchTask());

        setupUI();
        hide();
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    private void setupUI() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));

        isCaseSensitiveCheckBox = new JCheckBox("Case Sensitive");
        isCaseSensitiveCheckBox.setMnemonic('c');

        isCaseSensitiveCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                requestSearch();
            }
        });

        useRegularExpressionsCheckBox = new JCheckBox("Regular Expression");
        useRegularExpressionsCheckBox.setMnemonic('r');

        useRegularExpressionsCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                requestSearch();
            }
        });

        findNextButton = Utility.createButton(getClass(), "/org/gradle/gradleplugin/userinterface/swing/generic/tabs/move-down.png", "Find Next Match", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                goToNextMatch();
            }
        });
        findPreviousButton = Utility.createButton(getClass(), "/org/gradle/gradleplugin/userinterface/swing/generic/tabs/move-up.png", "Find Previous Match", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                goToPreviousMatch();
            }
        });

        JButton closeButton = Utility.createButton(getClass(), "/org/gradle/gradleplugin/userinterface/swing/generic/close.png", "Close Search Panel", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                hide();
            }
        });

        mainPanel.add(createTextToMatchField());
        mainPanel.add(Box.createHorizontalStrut(5));
        mainPanel.add(findPreviousButton);
        mainPanel.add(Box.createHorizontalStrut(5));
        mainPanel.add(findNextButton);
        mainPanel.add(Box.createHorizontalStrut(5));
        mainPanel.add(isCaseSensitiveCheckBox);
        mainPanel.add(Box.createHorizontalStrut(5));
        mainPanel.add(useRegularExpressionsCheckBox);
        addAdditionalFields(mainPanel);
        mainPanel.add(Box.createHorizontalGlue());
        mainPanel.add(closeButton);
    }

    private Component createTextToMatchField() {
        textToMatchField = new JTextField();
        textToMatchField.setMinimumSize(new Dimension(50, 10));

        //escape closes this dialog
        textToMatchField.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                hide();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        //hook up the key strokes that perform the search
        ActionListener performSearchNextAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                goToNextMatch();
            }
        };

        //hook up the key strokes that perform the search
        ActionListener performSearchPreviousAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                goToPreviousMatch();
            }
        };

        textToMatchField.registerKeyboardAction(performSearchNextAction, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        //F3 and Shift F3
        textToMatchField.registerKeyboardAction(performSearchNextAction, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        textToMatchField.registerKeyboardAction(performSearchPreviousAction, KeyStroke.getKeyStroke(KeyEvent.VK_F3, KeyEvent.SHIFT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

        //as the user types, perform a 'continue' search.
        textToMatchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                requestSearch();
            }

            public void removeUpdate(DocumentEvent e) {
                requestSearch();
            }

            public void changedUpdate(DocumentEvent e) {
                requestSearch();
            }
        });

        return textToMatchField;
    }

    /**
     * This adds a request to search on the queue.
     */
    private void requestSearch() {
        String textToMatch = textToMatchField.getText();
        boolean isCaseSensitive = isCaseSensitiveCheckBox.isSelected();
        boolean useRegularExpressions = useRegularExpressionsCheckBox.isSelected();
        String textToSearch = searchInteraction.getTextToSearch();

        searchRequests.offer(new SearchRequest(textToMatch, isCaseSensitive, useRegularExpressions, textToSearch));
    }


    /**
     * This waits until the next request is available.
     *
     * @return the last request.
     */
    private SearchRequest getNextAvailableRequest() {
        try {
            SearchRequest searchRequest = searchRequests.take();  //this will block until at least one request is on our queue.

            if (searchRequests.size() > 1)  //if we've got multiple requests, go get the latest.
            {
                List<SearchRequest> tasks = new ArrayList<SearchRequest>();
                searchRequests.drainTo(tasks);
                if (!tasks.isEmpty()) {
                    searchRequest = tasks.get(tasks.size() - 1); //we'll just use the latest
                }
            }

            return searchRequest;
        } catch (Exception e) {
            logger.error("Getting next available request", e);
            return null;
        }
    }

    private class SearchRequest {
        private String textToMatch;
        private boolean isCaseSensitive;
        private boolean useRegularExpressions;
        private String textToSearch;

        private SearchRequest(String textToMatch, boolean caseSensitive, boolean useRegularExpressions, String textToSearch) {
            this.textToMatch = textToMatch;
            isCaseSensitive = caseSensitive;
            this.useRegularExpressions = useRegularExpressions;
            this.textToSearch = textToSearch;
        }

        @Override
        public String toString() {
            return "textToMatch='" + textToMatch + '\''
                    + ", isCaseSensitive=" + isCaseSensitive
                    + ", useRegularExpressions=" + useRegularExpressions;
            //testToSearch is probably too long to show here
        }
    }

    private class SearchTask implements Runnable {
        private BasicTextSearchCriteria criteria = new BasicTextSearchCriteria();

        /**
         * When an object implementing interface <code>Runnable</code> is used to create a thread, starting the thread causes the object's <code>run</code> method to be called in that separately
         * executing thread. <p> The general contract of the method <code>run</code> is that it may take any action whatsoever.
         *
         * @see Thread#run()
         */
        public void run() {
            while (true) {
                SearchRequest request = getNextAvailableRequest();
                if (request != null) {
                    criteria.setTextToMatch(request.textToMatch);
                    criteria.setCaseSensitive(request.isCaseSensitive);
                    criteria.setUseRegularExpressions(request.useRegularExpressions);

                    editor.searchAllText(request.textToSearch, criteria);

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            searchInteraction.searchComplete(editor);
                            enableButtonsAndFieldsAppropriately(editor.hasMatches());
                        }
                    });
                }
            }
        }
    }

    /**
     * Call this to perform the last search again. This is useful if the search text has been changed behind our backs. This has no affect if we're not currently shown.
     */
    public void performSearchAgain() {
        if (mainPanel.isVisible()) {
            requestSearch();
        }
    }

    private void goToNextMatch() {
        TextBlockSearchEditor.SearchResult searchResult = editor.getNextSearchResult(searchInteraction.getCaretLocation());
        if (searchResult != null) {
            searchInteraction.highlightAndScrollToResult(editor, searchResult);
        }
    }

    private void goToPreviousMatch() {
        TextBlockSearchEditor.SearchResult searchResult = editor.getPreviousSearchResult(searchInteraction.getCaretLocation());
        if (searchResult != null) {
            searchInteraction.highlightAndScrollToResult(editor, searchResult);
        }
    }

    /**
     * You can override this to add additional fields to the given panel.
     *
     * <!      Name       Description>
     *
     * @param panel where to add your additional fields.
     */
    protected void addAdditionalFields(JPanel panel) {

    }

    /**
     * Call this to hide this panel.
     *
     */
    public void hide() {
        if (this.searchInteraction != null) {
            this.searchInteraction.removeResultHighlights();
        }

        mainPanel.setVisible(false);
    }

    /**
     * Call this to show this panel so that a search can begin. <!      Name              Description>
     *
     */
    public void show() {
        mainPanel.setVisible(true);
        showNormalColor();

        textToMatchField.selectAll();

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                textToMatchField.requestFocus();
            }
        });

        requestSearch();  //go ahead an perform a search based on what is currently present
    }


    private void showNormalColor() {
        textToMatchField.setForeground(UIManager.getColor("TextArea.foreground"));
    }

    private void showNoMatchColor() {
        textToMatchField.setForeground(notFoundColor);
    }

    public void enableButtonsAndFieldsAppropriately(boolean foundAMatch) {
        if (foundAMatch) {
            showNormalColor();
        } else {
            showNoMatchColor();
        }

        findNextButton.setEnabled(foundAMatch);
        findPreviousButton.setEnabled(foundAMatch);
    }
}
