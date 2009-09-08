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

import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.foundation.queue.ExecutionQueue;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.request.Request;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * This is a panel that displays the results of executing a gradle command.
 * It shows gradle's output as well as progress.
 *
 * @author mhunsicker
 */
public class OutputPanel extends JPanel implements ExecuteGradleCommandServerProtocol.ExecutionInteraction {
    private JPanel gradleOutputTextPanel;
    private JTextArea gradleOutputTextArea;

    private JPanel progressPanel;
    private JLabel progressLabel;
    private JProgressBar progressBar;

    private JPanel statusPanel;
    private JLabel statusLabel;

    private JLabel forceShowOutputButtonLabel;   //a label that acts like a button

    private boolean isBusy = false;     //is this actively showing output?
    private boolean isPending = false;  //is this waitin got show output?
    private boolean isPinned = false;   //keeps this panel open and disallows it from being re-used.
    private boolean showProgress = true;
    private boolean onlyShowOutputOnErrors = false;

    private Request request;

    public OutputPanel() {
        setupUI();
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public boolean isBusy() {
        return isBusy;
    }

    protected void setBusy(boolean busy) {
        isBusy = busy;
    }   //this should be the only way to isBusy.

    public boolean isPending() {
        return isPending;
    }

    public void setPending(boolean pending) {
        isPending = pending;
        if (isPending)
            statusLabel.setText("Waiting to execute");

        progressBar.setVisible(!isPending);
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public ExecutionQueue.Request getRequest() {
        return request;
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        add(createInfoPanel(), BorderLayout.NORTH);
        add(createGradleOutputPanel(), BorderLayout.CENTER);
    }

    private Component createGradleOutputPanel() {
        gradleOutputTextPanel = new JPanel(new BorderLayout());

        gradleOutputTextArea = new JTextArea();
        gradleOutputTextArea.setEditable(false);
        gradleOutputTextArea.setLineWrap(false);    //we only wrap on newlines, not in the middle of a line
        gradleOutputTextArea.setWrapStyleWord(false);

        //gradle formats some output in 'ascii art' fashion. This ensures things line up properly.
        Font font = new Font("Monospaced", Font.PLAIN, UIManager.getDefaults().getFont("Label.font").getSize());
        gradleOutputTextArea.setFont(font);

        JScrollPane scrollPane = new JScrollPane(gradleOutputTextArea);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        gradleOutputTextPanel.add(scrollPane, BorderLayout.CENTER);

        return gradleOutputTextPanel;
    }

    private Component createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(createStatusPanel());
        panel.add(createProgressPanel());

        return panel;
    }

    private Component createProgressPanel() {
        progressPanel = new JPanel(new BorderLayout());
        progressLabel = new JLabel("Progress");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);

        progressPanel.add(progressBar, BorderLayout.NORTH);
        progressPanel.add(progressLabel, BorderLayout.SOUTH);

        progressPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        progressPanel.setVisible(false);
        return progressPanel;
    }

    private Component createStatusPanel() {
        statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
        statusLabel = new JLabel();

        //this button is only shown when the output is hidden
        forceShowOutputButtonLabel = new JLabel("Show Output");

        forceShowOutputButtonLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                forciblyShowOutput();
            }

            public void mouseEntered(MouseEvent e) {
                forceShowOutputButtonLabel.setForeground(UIManager.getColor("textHighlightText"));
            }

            public void mouseExited(MouseEvent e) {
                forceShowOutputButtonLabel.setForeground(UIManager.getColor("Label.foreground"));
            }
        });

        statusPanel.add(statusLabel);
        statusPanel.add(Box.createHorizontalGlue());
        statusPanel.add(forceShowOutputButtonLabel);

        statusPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        return statusPanel;
    }

    /**
       Call this if you're going to reuse this. it resets its output.
    */
    public void reset() {
        statusLabel.setText("");
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        gradleOutputTextArea.setText("");
        progressLabel.setText("");
    }

    /**
       Call this to append text to the gradle output field. We'll also move the
       caret to the end.

       @param  text       the text to add
    */
    private void appendGradleOutput(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                gradleOutputTextArea.insert(text, gradleOutputTextArea.getDocument().getLength());
                gradleOutputTextArea.setCaretPosition(gradleOutputTextArea.getDocument().getLength());
            }
        });
    }

    private void setProgress(String text, float percentComplete) {
        progressBar.setValue((int) percentComplete);
        progressLabel.setText(text);
    }

    /**
       Notification that execution of a task or tasks has been started.
    */
    public void reportExecutionStarted() {
        setPending(false);
        setBusy(true);
        setProgress("Starting", 0);
        if (showProgress)
            progressPanel.setVisible(true);

        statusLabel.setText("Executing");

        //give the user the option to override this.
        forceShowOutputButtonLabel.setVisible(onlyShowOutputOnErrors);
    }

    /**
     * Notification that execution of all tasks has completed. This is only called
     * once at the end.
     *
     * @param wasSuccessful whether or not gradle encountered errors.
     * @param buildResult   contains more detailed information about the result of a build.
     * @param output        the text that gradle produced. May contain error
     *                      information, but is usually just status.
     */
    public void reportExecutionFinished(boolean wasSuccessful, BuildResult buildResult, String output) {
        reportExecutionFinished(wasSuccessful, output, buildResult.getFailure());
    }

    /**
     * Notification that execution of a task has completed. This is the task you
     * initiated and not for each subtask or dependent task.
     * @param  wasSuccessful whether or not gradle encountered errors.
     * @param  output        the text that gradle produced. May contain error
     *                       information, but is usually just status.
     * @param throwable
    */
    public void reportExecutionFinished(boolean wasSuccessful, String output, Throwable throwable) {
        setPending(false); //this can be called before we actually get a start message if it fails early. This clears the pending flag so we know we can reuse it.
        setBusy(false);
        progressPanel.setVisible(false);

        if (gradleOutputTextArea.getDocument().getLength() == 0)   //if its empty,
            appendGradleOutput(output);                            //add our output. This can happen if execution fails for internal purposes (and we we'll get wasSuccessful because the message wasn't bubbled up to us).

        //show the user the time we finished this.
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm:ss aa");
        String formattedTime = formatter.format(Calendar.getInstance().getTime());

        if (wasSuccessful) {
            statusLabel.setText("Completed successfully at " + formattedTime);
            appendGradleOutput("\nCompleted Successfully");
        } else {
            statusLabel.setText("Completed with errors at " + formattedTime);
            statusLabel.setForeground(Color.red.darker());

            //since errors occurred, show the output. If onlyShowOutputOnErrors is false, this textPanel will already be visible.
            gradleOutputTextPanel.setVisible(true);
        }

        appendThrowable(throwable);

        //lastly, if the text output is not visible, make the 'show output' button visible
        forceShowOutputButtonLabel.setVisible(!gradleOutputTextPanel.isVisible());
    }

    private void appendThrowable(Throwable throwable) {
        if (throwable != null) {
            String output = GradlePluginLord.getGradleExceptionMessage(throwable, StartParameter.ShowStacktrace.ALWAYS_FULL);
            appendGradleOutput(output);
        }
    }

    /**
       Notification that a single task has completed. Note: the task you kicked
       off probably executes other tasks.

       @param  currentTaskName the task being executed
       @param  percentComplete the percent complete of all the tasks that make
                               up the task you requested.
    */
    public void reportTaskStarted(String currentTaskName, float percentComplete) {
        setProgress(currentTaskName, percentComplete);
    }

    public void reportTaskComplete(String currentTaskName, float percentComplete) {
        setProgress(currentTaskName, percentComplete);
    }

    public void reportFatalError(String message) {
        appendGradleOutput('\n' + message + "\n\nFailed.\n");
    }

    /**
    Report real-time output from gradle and its subsystems (such as ant).
    @param  output     a single line of text to show.
    @author mhunsicker
    */
    public void reportLiveOutput(String output) {
        appendGradleOutput(output);
    }

    /**
       Determines if this panel is ready to be reused. Currently, if its not
       busy or pinned, it can be reused.
       @author mhunsicker
    */
    public boolean canBeReusedNow() {
        return !isPending && !isBusy && !isPinned;
    }

    /**
       Call this to show progress. Some tasks have no useful progress, so this
       allows you to disable it.
       @param  showProgress true to show a progress bar, false not to.
    */
    public void showProgress(boolean showProgress) {
        this.showProgress = showProgress;
        progressPanel.setVisible(showProgress);
    }

    /**
       This overrides the onlyShowOutputOnErrors
    */
    private void forciblyShowOutput() {
        gradleOutputTextPanel.setVisible(true);
        forceShowOutputButtonLabel.setVisible(false);
    }

    public void setOnlyShowOutputOnErrors(boolean value) {
        this.onlyShowOutputOnErrors = value;
        gradleOutputTextPanel.setVisible(!value);
    }

    /**
       This returns the current text area object.
       @return the text area or null if there are not text areas (or none visible).
    */
    public JTextArea getSelectedOutputTextArea() {
        if (gradleOutputTextArea.isVisible())
            return gradleOutputTextArea;

        return null;
    }

    public boolean getOnlyShowOutputOnErrors() {
        return onlyShowOutputOnErrors;
    }

    public boolean close() {
        if (request != null)   //if we have a request, we can only close if it allows us to.
        {
            if (!request.cancel())
                return false;
        }

        setPinned(false);  //unpin it when it is removed
        return true;
    }
}
