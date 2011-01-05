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

import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.foundation.output.FileLinkDefinitionLord;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * This is a panel that displays the results of executing a gradle command. It shows gradle's output as well as progress.
 *
 * @author mhunsicker
 */
public class OutputPanel extends JPanel implements ExecuteGradleCommandServerProtocol.ExecutionInteraction {

    private OutputPanelParent parent;
    private AlternateUIInteraction alternateUIInteraction;

    private JPanel gradleOutputTextPanel;
    private OutputTextPane gradleOutputTextPane;

    private JPanel progressPanel;
    private JLabel progressLabel;
    private JProgressBar progressBar;

    private JPanel statusPanel;
    private JLabel statusLabel;

    private JButton executeAgainButton;

    private JLabel forceShowOutputButtonLabel;   //a label that acts like a button

    private boolean isBusy;     //is this actively showing output?
    private boolean isPending;  //is this waitin got show output?
    private boolean isPinned;   //keeps this panel open and disallows it from being re-used.
    private boolean showProgress = true;
    private boolean onlyShowOutputOnErrors;

    private Request request;

    public interface OutputPanelParent {

        public void removeOutputPanel(OutputPanel outputPanel);

        void reportExecuteFinished(Request request, boolean wasSuccessful);

        void executeAgain(Request request, OutputPanel outputPanel);

        public FileLinkDefinitionLord getFileLinkDefinitionLord();
    }

    public OutputPanel(OutputPanelParent parent, AlternateUIInteraction alternateUIInteraction) {
        this.parent = parent;
        this.alternateUIInteraction = alternateUIInteraction;
    }

    /**
     * Call this after initializing this, but after setting any additional swing properties (actually, just the font for now). I really only added this as an optimization. Since we'll always be setting
     * the font, I didn't want the various style objects created only to be thrown away and re-created. This way, you can set the font before we create the styles.
     */
    public void initialize() {
        setupUI();
    }

    /**
     * This is called whenever a new request is made. It associates this request with this output panel.
     */
    public void setRequest(Request request, boolean onlyShowOutputOnErrors) {
        this.request = request;
        if (request.forceOutputToBeShown()) {
            setOnlyShowOutputOnErrors(false);
        } else {
            setOnlyShowOutputOnErrors(onlyShowOutputOnErrors);
        }

        //set this to indeterminate until we figure out how many tasks to execute.
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(false); //And don't show '0%' in the mean time.

        setPending(true);
        showProgress(true);   //make sure the progress is shown. It may have been turned off if we're reusing this component

        appendGradleOutput(getPrefixText());
    }

    /**
     * Returns a string stating the command we're currently executing. This is placed at the beginning of the output text. This is called when we start and when the command is finished (where we
     * replace all of our text with the total output)
     */
    private String getPrefixText() {
        return "Executing command: \"" + request.getFullCommandLine() + "\"\n";
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

    private void setPending(boolean pending) {
        isPending = pending;
        if (isPending) {
            statusLabel.setText("Waiting to execute");
        }

        progressBar.setVisible(!isPending);
    }

    public Request getRequest() {
        return request;
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        add(createInfoPanel(), BorderLayout.NORTH);
        add(createGradleOutputPanel(), BorderLayout.CENTER);
    }

    private Component createGradleOutputPanel() {
        gradleOutputTextPanel = new JPanel(new BorderLayout());

        gradleOutputTextPane = new OutputTextPane(new OutputTextPane.Interaction() {
            public void fileClicked(File file, int line) {
                alternateUIInteraction.openFile(file, line);
            }
        }, alternateUIInteraction.doesSupportEditingOpeningFiles(), getFont(), parent.getFileLinkDefinitionLord());

        gradleOutputTextPanel.add(gradleOutputTextPane.asComponent(), BorderLayout.CENTER);

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
        executeAgainButton = Utility.createButton(OutputPanel.class, "/org/gradle/gradleplugin/userinterface/swing/generic/tabs/execute.png", "Execute Again", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                parent.executeAgain(request, OutputPanel.this);
            }
        });
        executeAgainButton.setVisible(false);

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

        statusPanel.add(executeAgainButton);
        statusPanel.add(Box.createHorizontalStrut(2));
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createHorizontalGlue());
        statusPanel.add(forceShowOutputButtonLabel);

        statusPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        return statusPanel;
    }

    /**
     * Call this if you're going to reuse this. it resets its output.
     */
    public void reset() {
        executeAgainButton.setVisible(false);
        statusLabel.setText("");
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        gradleOutputTextPane.setText("");
        progressLabel.setText("");
    }

    /**
     * Call this to append text to the gradle output field. We'll also move the caret to the end.
     *
     * @param text the text to add
     */
    private void appendGradleOutput(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                gradleOutputTextPane.appendText(text);
            }
        });
    }

    private void setProgress(final String text, final float percentComplete) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                progressBar.setValue((int) percentComplete);
                progressLabel.setText(text);
            }
        });
    }

    /**
     * Notification that execution of a task or tasks has been started.
     */
    public void reportExecutionStarted() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setPending(false);
                setBusy(true);
                setProgress("Starting", 0);
                if (showProgress) {
                    progressPanel.setVisible(true);
                }

                statusLabel.setText("Executing");

                //give the user the option to override this.
                forceShowOutputButtonLabel.setVisible(onlyShowOutputOnErrors);
            }
        });
    }

    /**
     * Notification of the total number of tasks that will be executed. This is called after reportExecutionStarted and before any tasks are executed.
     *
     * @param size the total number of tasks.
     */
    public void reportNumberOfTasksToExecute(final int size) {  //if we only have a single task, then the intire process will be indeterminately long (it'll just from 0 to 100)
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                boolean isIndeterminate = size == 1;
                progressBar.setIndeterminate(isIndeterminate);
                progressBar.setStringPainted(!isIndeterminate);
            }
        });
    }

    /**
     * Notification that execution of all tasks has completed. This is only called once at the end.
     *
     * @param wasSuccessful whether or not gradle encountered errors.
     * @param buildResult contains more detailed information about the result of a build.
     * @param output the text that gradle produced. May contain error information, but is usually just status.
     */
    public void reportExecutionFinished(boolean wasSuccessful, BuildResult buildResult, String output) {
        reportExecutionFinished(wasSuccessful, output, buildResult.getFailure());
    }

    /**
     * Notification that execution of a task has completed. This is the task you initiated and not for each subtask or dependent task.
     *
     * @param wasSuccessful whether or not gradle encountered errors.
     * @param output the text that gradle produced. May contain error information, but is usually just status.
     */
    public void reportExecutionFinished(final boolean wasSuccessful, final String output, final Throwable throwable) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setPending(false); //this can be called before we actually get a start message if it fails early. This clears the pending flag so we know we can reuse it.
                setBusy(false);
                progressPanel.setVisible(false);

                //Make the output equal to all of our output. There are some timing issues where we don't get the last live output from gradle.
                //This 'output' is the entire text. This way we always get all output.
                String newText = getPrefixText() + output;
                gradleOutputTextPane.setText(newText);

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

                executeAgainButton.setVisible(true);

                appendThrowable(throwable);

                //lastly, if the text output is not visible, make the 'show output' button visible
                forceShowOutputButtonLabel.setVisible(!gradleOutputTextPanel.isVisible());

                parent.reportExecuteFinished(request, wasSuccessful);
            }
        });
    }

    private void appendThrowable(Throwable throwable) {
        if (throwable != null) {
            String output = GradlePluginLord.getGradleExceptionMessage(throwable, StartParameter.ShowStacktrace.ALWAYS_FULL);
            appendGradleOutput(output);
        }
    }

    /**
     * Notification that a single task has completed. Note: the task you kicked off probably executes other tasks.
     *
     * @param currentTaskName the task being executed
     * @param percentComplete the percent complete of all the tasks that make up the task you requested.
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
     * Report real-time output from gradle and its subsystems (such as ant).
     *
     * @param output a single line of text to show.
     * @author mhunsicker
     */
    public void reportLiveOutput(String output) {
        appendGradleOutput(output);
    }

    /**
     * Determines if this panel is ready to be reused. Currently, if its not busy or pinned, it can be reused.
     *
     * @author mhunsicker
     */
    public boolean canBeReusedNow() {
        return !isPending && !isBusy && !isPinned;
    }

    /**
     * Call this to show progress. Some tasks have no useful progress, so this allows you to disable it.
     *
     * @param showProgress true to show a progress bar, false not to.
     */
    private void showProgress(boolean showProgress) {
        this.showProgress = showProgress;
        progressPanel.setVisible(showProgress);
    }

    /**
     * This overrides the onlyShowOutputOnErrors
     */
    private void forciblyShowOutput() {
        gradleOutputTextPanel.setVisible(true);
        forceShowOutputButtonLabel.setVisible(false);
    }

    public void setOnlyShowOutputOnErrors(boolean value) {
        this.onlyShowOutputOnErrors = value;
        gradleOutputTextPanel.setVisible(!value);
    }

    public boolean getOnlyShowOutputOnErrors() {
        return onlyShowOutputOnErrors;
    }

    public boolean close() {
        if (request != null)   //if we have a request, we can only close if it allows us to.
        {
            if (!request.cancel()) {
                return false;
            }
        }

        parent.removeOutputPanel(this);

        setPinned(false);  //unpin it when it is removed
        return true;
    }

    /**
     * Sets the font for this component.
     *
     * @param font the desired <code>Font</code> for this component
     * @beaninfo preferred: true bound: true attribute: visualUpdate true description: The font for the component.
     * @see Component#getFont
     */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
        if (gradleOutputTextPane != null)  //this gets called by internal Swing APIs, so we may not have this yet.
        {
            gradleOutputTextPane.setFont(font);
        }
    }
}
