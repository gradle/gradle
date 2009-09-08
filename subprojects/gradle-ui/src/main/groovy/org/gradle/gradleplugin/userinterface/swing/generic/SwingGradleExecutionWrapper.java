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

import org.gradle.foundation.CommandLineAssistant;
import org.gradle.foundation.TaskView;
import org.gradle.foundation.queue.ExecutionQueue;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class wraps executing gradle tasks and displays the results in a panel
 * inside a JTabbedPane. It can reuse existing tabs but creates new ones if you
 * run mutliple things concurrently.
 * This exists so other UI components can launch gradle tasks without having to
 * deal directly with gradle or with handling the output.
 * We've decided to launch gradle as a separate process. This eliminates numerous
 * issues related to classloaders and thigns that are already defined in the
 * launching scripts.
 *
 * @author mhunsicker
 */
public class SwingGradleExecutionWrapper {
    private GradlePluginLord gradlePluginLord;
    private AlternateUIInteraction alternateUIInteraction;

    private JPanel mainPanel;
    private JTabbedPane tabbedPane;

    private JPopupMenu popupMenu;

    private boolean onlyShowOutputOnErrors = false;
    private JMenuItem closeMenuItem;
    private JMenuItem closeAllMenuItem;
    private JMenuItem closeAllButThisMenuItem;
    private JMenuItem togglePinStateMenuItem;

    public SwingGradleExecutionWrapper(GradlePluginLord gradlePluginLord2, AlternateUIInteraction alternateUIInteraction1) {
        this.gradlePluginLord = gradlePluginLord2;
        this.alternateUIInteraction = alternateUIInteraction1;

        setupUI();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public GradlePluginLord getGradlePluginLord() {
        return gradlePluginLord;
    }

    private void setupUI() {
        mainPanel = new JPanel(new BorderLayout());

        tabbedPane = new JTabbedPane();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        setupPopupMenu();
    }

    private void setupPopupMenu() {
        popupMenu = new JPopupMenu();

        closeMenuItem = new JMenuItem(new AbstractAction("Close") {
            public void actionPerformed(ActionEvent e) {
                closeSelectedTab();
            }
        });
        popupMenu.add(closeMenuItem);

        closeAllMenuItem = new JMenuItem(new AbstractAction("Close All") {
            public void actionPerformed(ActionEvent e) {
                closeAllTabs();
            }
        });
        popupMenu.add(closeAllMenuItem);

        closeAllButThisMenuItem = new JMenuItem(new AbstractAction("Close All But This") {
            public void actionPerformed(ActionEvent e) {
                closeAllButSelectedTab();
            }
        });

        popupMenu.add(closeAllButThisMenuItem);
        popupMenu.addSeparator();

        togglePinStateMenuItem = new JMenuItem(new AbstractAction("Pin") {
            public void actionPerformed(ActionEvent e) {
                togglePinSelectedTab();
            }
        });

        popupMenu.add(togglePinStateMenuItem);

        tabbedPane.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON3) {
                    enablePopupMenuAppropriately();
                    Point point = e.getPoint();
                    popupMenu.show(tabbedPane, point.x, e.getPoint().y);
                }
            }
        });
    }

    private void enablePopupMenuAppropriately() {
        OutputPanel panel = getSelectedOutputPanel();
        if (panel == null) {
            closeMenuItem.setEnabled(false);
            togglePinStateMenuItem.setEnabled(false);
        } else {
            closeMenuItem.setEnabled(true);

            //change the name of this to reflect what is actually happening.
            if (panel.isPinned())
                togglePinStateMenuItem.setText("Unpin");
            else
                togglePinStateMenuItem.setText("Pin");
        }
    }

    /**
       This obtains an output panel for executing a task. It will try to reuse
       an existing tab.

       I don't like how this mechanism works. Its not obvious what you're going
       to get and how the tabs will be reused (from a user's standpoint). IntelliJ
       Idea doesn't allow multiple compiles/builds at a time, so they don't have
       this issue there. They do have it on Find where they have an option to
       explicitly display in a new tab. I don't think that quite works here
       as you don't normally think about the output. This is only an issue if
       you run multiple tasks at once or try to run new tasks while others are
       still executing. Ultimately, I don't think tabs are the way to go because
       closing a bunch of tabs is a pain.

       @param  description          the title we'll give to the output.
       @param  forceOutputToBeShown overrides the user setting onlyShowOutputOnErrors
                                    so that the output is shown regardless
       @param selectOutputPanel true to select the output panel after we setup
                                the tab, false if not. This is really only useful
                                if you're calling this for multiple tasks one
                                right after the other. Pass in false for all but
                                the first (or last) one depending on what you want.
       @param reuseSelectedOutputPanelFirst true to attempt to reuse the current
                                output tab. Otherwise, we'll go from left to right
                                looking for a tab to reuse. This is really only
                                useful if you're calling this for multiple tasks
                                one after the other. In that case, you probably
                                want to pass in false.
       @return an output panel.
    */
    private OutputPanel getOutputPanelForExecution(String description, boolean forceOutputToBeShown, boolean selectOutputPanel, boolean reuseSelectedOutputPanelFirst) {
        OutputTab outputPanel = findExistingOutputPanelForExecution(reuseSelectedOutputPanelFirst);
        if (outputPanel != null) {
            outputPanel.setTabHeaderText(description);
            outputPanel.reset();
        } else {  //we don't have an existing tab. Create a new one.
            outputPanel = new OutputTab(tabbedPane, description);
            tabbedPane.addTab(description, outputPanel);
            if (selectOutputPanel)
                tabbedPane.setSelectedComponent(outputPanel);

            Utility.setTabComponent15Compatible(tabbedPane, tabbedPane.getTabCount() - 1, outputPanel.getTabHeader());
        }

        if (forceOutputToBeShown)
            outputPanel.setOnlyShowOutputOnErrors(false);
        else
            outputPanel.setOnlyShowOutputOnErrors(onlyShowOutputOnErrors);

        return outputPanel;
    }

    /**
       This locates an existing panel to reuse.
    */
    private OutputTab findExistingOutputPanelForExecution(boolean considerSelectedTabFirst) {
        OutputTab outputPanel = null;
        if (considerSelectedTabFirst) {
            outputPanel = (OutputTab) tabbedPane.getSelectedComponent();
            if (outputPanel != null && outputPanel.canBeReusedNow())
                return outputPanel;
        }

        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            outputPanel = (OutputTab) iterator.next();
            if (outputPanel.canBeReusedNow())
                return outputPanel;
        }

        return null;
    }

    /**
       @return a list of all the output panels currenly in the tabbed pane.
    */
    private List<OutputPanel> getOutputPanels() {
        List<OutputPanel> panels = new ArrayList<OutputPanel>();
        for (int index = 0; index < tabbedPane.getTabCount(); index++) {
            OutputPanel outputPanel = (OutputPanel) tabbedPane.getComponentAt(index);
            panels.add(outputPanel);
        }

        return panels;
    }

    /**
       Call this to refresh the task list in a background thread. This creates
       or uses an existing OutputPanel to display the results.
    */
    public void refreshTaskTree() {
        OutputPanel outputPanel = getOutputPanelForExecution("Refresh", false, true, true);

        outputPanel.setPending(true);
        outputPanel.showProgress(true);   //make sure the progress is shown. It may have been turned off it we're reusing this component
        Request request = gradlePluginLord.addRefreshRequestToQueue(outputPanel);
        if (request == null)
            outputPanel.close();

        outputPanel.setRequest(request);
    }

    /**
       Call this to execute a task in a background thread. This creates or uses
       an existing OutputPanel to display the results.

       @param  task       the task to execute.
       @param forceOutputToBeShown overrides the user setting onlyShowOutputOnErrors
                            so that the output is shown regardless
    */
    public void executeTaskInThread(final TaskView task, boolean forceOutputToBeShown, String... additionCommandLineOptions) {
        String fullCommandLine = CommandLineAssistant.appendAdditionalCommandLineOptions(task, additionCommandLineOptions);
        executeTaskInThread(fullCommandLine, task.getFullTaskName(), forceOutputToBeShown, true, true);
    }

    public void executeTasksInThread(final List<TaskView> tasks, boolean forceOutputToBeShown, String... additionCommandLineOptions) {
        boolean isFirstTask = true;   //this translates to the selecting only the first output panel.

        Iterator<TaskView> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            TaskView task = iterator.next();

            String fullCommandLine = CommandLineAssistant.appendAdditionalCommandLineOptions(task, additionCommandLineOptions);
            executeTaskInThread(fullCommandLine, task.getFullTaskName(), forceOutputToBeShown, isFirstTask, false);
            isFirstTask = false;
        }
    }


    /**
       Call this to execute a task in a background thread. This creates or uses
       an existing OutputPanel to display the results. This version takes text
       instead of a task object.

       @param  fullCommandLine the full command line to pass to gradle.
       @param  displayName     what we show on the tab.
       @param forceOutputToBeShown overrides the user setting onlyShowOutputOnErrors
                            so that the output is shown regardless
       @param selectOutputPanel true to select the output panel after we setup
                                the tab, false if not. This is really only useful
                                if you're calling this for multiple tasks one
                                right after the other. Pass in false for all but
                                the first (or last) one depending on what you want.
       @param reuseSelectedOutputPanelFirst true to attempt to reuse the current
                                output tab. Otherwise, we'll go from left to right
                                looking for a tab to reuse. This is really only
                                useful if you're calling this for multiple tasks
                                one after the other. In that case, you probably
                                want to pass in false.
    */
    public void executeTaskInThread(String fullCommandLine, String displayName, boolean forceOutputToBeShown, boolean selectOutputPanel, boolean reuseSelectedOutputPanelFirst) {
        if (fullCommandLine == null)
            return;

        OutputPanel outputPanel = getOutputPanelForExecution("Execute '" + displayName + "'", forceOutputToBeShown, selectOutputPanel, reuseSelectedOutputPanelFirst);

        outputPanel.setPending(true);
        outputPanel.showProgress(true);   //make sure the progress is shown. It may have been turned off if we're reusing this component
        Request request = gradlePluginLord.addExecutionRequestToQueue(fullCommandLine, outputPanel);
        outputPanel.setRequest(request);
    }

    /**
       Determines if any tasks are currently being run. We check all of our
       OutputPanels.
       @return true if we're busy, false if not.
    */
    public boolean isBusy() {
        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            OutputPanel outputPanel = iterator.next();
            if (outputPanel.isBusy())
                return true;
        }
        return false;
    }

    public void setOnlyShowOutputOnErrors(boolean value) {
        this.onlyShowOutputOnErrors = value;
    }

    public boolean getOnlyShowOutputOnErrors() {
        return onlyShowOutputOnErrors;
    }

    private void closeSelectedTab() {
        OutputTab component = getSelectedOutputPanel();
        if (component != null)
            component.close();
    }

    private void closeAllTabs() {
        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            OutputPanel outputPanel = iterator.next();
            outputPanel.close();
        }
    }

    private void closeAllButSelectedTab() {
        OutputTab component = getSelectedOutputPanel();
        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            OutputPanel outputPanel = iterator.next();
            if (outputPanel != component)
                outputPanel.close();
        }
    }

    /**
       Changes the current pinned status of the selected tab.
    */
    private void togglePinSelectedTab() {
        OutputTab component = getSelectedOutputPanel();
        if (component != null)
            component.setPinned(!component.isPinned());
    }

    private OutputTab getSelectedOutputPanel() {
        return (OutputTab) tabbedPane.getSelectedComponent();
    }

    //return the output panel for the specified request.
    private OutputPanel getOutputPanel(ExecutionQueue.Request request) {
        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            OutputPanel outputPanel = iterator.next();
            if (outputPanel.getRequest() == request)
                return outputPanel;
        }
        return null;
    }

    private JTextArea getSelectedOutputTextArea() {
        OutputTab component = getSelectedOutputPanel();
        if (component == null)
            return null;

        return component.getSelectedOutputTextArea();
    }
}
