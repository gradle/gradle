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

import org.gradle.foundation.common.ObserverLord;
import org.gradle.foundation.output.FileLinkDefinitionLord;
import org.gradle.foundation.queue.ExecutionQueue;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class manages displaying the results of a gradle execution in a panel inside a JTabbedPane. It can reuse existing tabs but creates new ones if you run multiple things concurrently.
 */
public class OutputPanelLord implements OutputUILord, GradlePluginLord.RequestObserver, OutputPanel.OutputPanelParent {

    private JPanel mainPanel;
    private JTabbedPane tabbedPane;

    private JPopupMenu popupMenu;

    private boolean onlyShowOutputOnErrors;
    private JMenuItem closeMenuItem;
    private JMenuItem closeAllMenuItem;
    private JMenuItem closeAllButThisMenuItem;
    private JMenuItem togglePinStateMenuItem;

    private ObserverLord<OutputObserver> observerLord = new ObserverLord<OutputObserver>();
    private GradlePluginLord gradlePluginLord;
    private AlternateUIInteraction alternateUIInteraction;
    private Font font;

    private FileLinkDefinitionLord fileLinkDefinitionLord;

    private ExecutionRequest lastExecutionRequest;

    public OutputPanelLord(GradlePluginLord gradlePluginLord, AlternateUIInteraction alternateUIInteraction) {
        this.gradlePluginLord = gradlePluginLord;
        this.alternateUIInteraction = alternateUIInteraction;

        fileLinkDefinitionLord = new FileLinkDefinitionLord();

        //add the OutputPanelLord as a request observer so it can create new tabs when new requests are added.
        gradlePluginLord.addRequestObserver(this, true);

        setupUI();

        //gradle formats some output in 'ascii art' fashion. This ensures things line up properly.
        Font font = new Font("Monospaced", Font.PLAIN, UIManager.getDefaults().getFont("Label.font").getSize());

        setOutputTextFont(font);
    }

    public JPanel getMainPanel() {
        return mainPanel;
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
            if (panel.isPinned()) {
                togglePinStateMenuItem.setText("Unpin");
            } else {
                togglePinStateMenuItem.setText("Pin");
            }
        }
    }

    /**
     * This obtains an output panel for executing a task. It will try to reuse an existing tab.
     *
     * I don't like how this mechanism works. Its not obvious what you're going to get and how the tabs will be reused (from a user's standpoint). IntelliJ Idea doesn't allow multiple compiles/builds
     * at a time, so they don't have this issue there. They do have it on Find where they have an option to explicitly display in a new tab. I don't think that quite works here as you don't normally
     * think about the output. This is only an issue if you run multiple tasks at once or try to run new tasks while others are still executing. Ultimately, I don't think tabs are the way to go
     * because closing a bunch of tabs is a pain.
     *
     * @param description the title we'll give to the output.
     * @param selectOutputPanel true to select the output panel after we setup the tab, false if not. This is really only useful if you're calling this for multiple tasks one right after the other.
     * Pass in false for all but the first (or last) one depending on what you want.
     * @param reuseSelectedOutputPanelFirst true to attempt to reuse the current output tab. Otherwise, we'll go from left to right looking for a tab to reuse. This is really only useful if you're
     * calling this for multiple tasks one after the other. In that case, you probably want to pass in false.
     * @return an output panel.
     */
    private OutputPanel getOutputPanelForExecution(String description, boolean selectOutputPanel, boolean reuseSelectedOutputPanelFirst) {
        OutputTab outputPanel = findExistingOutputPanelForExecution(reuseSelectedOutputPanelFirst);
        if (outputPanel != null) {
            outputPanel.setTabHeaderText(description);
            outputPanel.reset();
        } else {  //we don't have an existing tab. Create a new one.
            outputPanel = new OutputTab(gradlePluginLord, this, description, alternateUIInteraction);
            outputPanel.setFont(font);
            outputPanel.initialize();
            outputPanel.reset();
            tabbedPane.addTab(description, outputPanel);
            if (selectOutputPanel) {
                tabbedPane.setSelectedComponent(outputPanel);
            }

            Utility.setTabComponent15Compatible(tabbedPane, tabbedPane.getTabCount() - 1, outputPanel.getTabHeader());
        }

        return outputPanel;
    }

    /**
     * This locates an existing panel to reuse.
     */
    private OutputTab findExistingOutputPanelForExecution(boolean considerSelectedTabFirst) {
        OutputTab outputPanel = null;
        if (considerSelectedTabFirst) {
            outputPanel = (OutputTab) tabbedPane.getSelectedComponent();
            if (outputPanel != null && outputPanel.canBeReusedNow()) {
                return outputPanel;
            }
        }

        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            outputPanel = (OutputTab) iterator.next();
            if (outputPanel.canBeReusedNow()) {
                return outputPanel;
            }
        }

        return null;
    }

    /**
     * @return a list of all the output panels currenly in the tabbed pane.
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
     * This formats a display name so it isn't too long. The actual size is purely arbitrary.
     *
     * @param displayName the current display name
     * @return a display name that isn't too long to display on tabs.
     */
    private String reformatDisplayName(String displayName) {
        if (displayName.length() <= 20) {
            return displayName;   //its fine
        }

        //I'm going 6 characters less because it looks stupid to replace 3 characters with 3 characters.
        //There's no absolute amount here, this just seems to look better.
        return displayName.substring(0, 14) + "...";
    }

    /**
     * Determines if any tasks are currently being run. We check all of our OutputPanels.
     *
     * @return true if we're busy, false if not.
     */
    public boolean isBusy() {
        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            OutputPanel outputPanel = iterator.next();
            if (outputPanel.isBusy()) {
                return true;
            }
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
        if (component != null) {
            component.close();
        }
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
            if (outputPanel != component) {
                outputPanel.close();
            }
        }
    }

    /**
     * Changes the current pinned status of the selected tab.
     */
    private void togglePinSelectedTab() {
        OutputTab component = getSelectedOutputPanel();
        if (component != null) {
            component.setPinned(!component.isPinned());
        }
    }

    private OutputTab getSelectedOutputPanel() {
        return (OutputTab) tabbedPane.getSelectedComponent();
    }

    //return the output panel for the specified request.
    private OutputPanel getOutputPanel(ExecutionQueue.Request request) {
        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            OutputPanel outputPanel = iterator.next();
            if (outputPanel.getRequest() == request) {
                return outputPanel;
            }
        }
        return null;
    }

    public void executeAgain(Request request, OutputPanel outputPanel) {
        //this needs to work better. It needs to do the execute again in the same
        //OutputPanel. However, because this generically listens for requests and
        //adds them to this panel, things are more complicated.
        request.executeAgain(gradlePluginLord);
    }

    public void reportExecuteFinished(final Request request, final boolean wasSuccessful) {
        observerLord.notifyObservers(new ObserverLord.ObserverNotification<OutputObserver>() {
            public void notify(OutputObserver observer) {
                observer.reportExecuteFinished(request, wasSuccessful);
            }
        });
    }

    public void removeOutputPanel(final OutputPanel outputPanel) {
        tabbedPane.remove(outputPanel);

        observerLord.notifyObservers(new ObserverLord.ObserverNotification<OutputObserver>() {
            public void notify(OutputObserver observer) {
                observer.outputTabClosed(outputPanel.getRequest());
            }
        });
    }

    public void executionRequestAdded(final ExecutionRequest request) {
        lastExecutionRequest = request;

        String displayName = reformatDisplayName(request.getDisplayName());
        requestAdded(request, "Execute '" + displayName + "'");
        observerLord.notifyObservers(new ObserverLord.ObserverNotification<OutputObserver>() {
            public void notify(OutputObserver observer) {
                observer.executionRequestAdded(request);
            }
        });
    }

    public void refreshRequestAdded(final RefreshTaskListRequest request) {
        requestAdded(request, "Refresh");
        observerLord.notifyObservers(new ObserverLord.ObserverNotification<OutputObserver>() {
            public void notify(OutputObserver observer) {
                observer.refreshRequestAdded(request);
            }
        });
    }

    private void requestAdded(Request request, String name) {
        OutputPanel outputPanel = getOutputPanelForExecution(name, false, true);

        outputPanel.setRequest(request, onlyShowOutputOnErrors);
        request.setExecutionInteraction(outputPanel);
    }

    /**
     * Notification that a command is about to be executed. This is mostly useful for IDE's that may need to save their files.
     *
     * @param request the request to be executed
     */
    public void aboutToExecuteRequest(Request request) {
    }

    /**
     * Notification that the command has completed execution.
     *
     * @param request the original request containing the command that was executed
     * @param result the result of the command
     * @param output the output from gradle executing the command
     */
    public void requestExecutionComplete(Request request, int result, String output) {

    }

    public void addOutputObserver(OutputObserver observer, boolean inEventQueue) {
        observerLord.addObserver(observer, inEventQueue);
    }

    public void removeOutputObserver(OutputObserver observer) {
        observerLord.removeObserver(observer);
    }

    public int getTabCount() {
        return tabbedPane.getTabCount();
    }

    /**
     * Sets the font for the output text
     *
     * @param font the new font
     */
    public void setOutputTextFont(Font font) {

        this.font = font;
        Iterator<OutputPanel> iterator = getOutputPanels().iterator();
        while (iterator.hasNext()) {
            OutputPanel outputPanel = iterator.next();
            outputPanel.setFont(font);
        }
    }

    public Font getOutputTextFont() {
        return font;
    }

    public FileLinkDefinitionLord getFileLinkDefinitionLord() {
        return fileLinkDefinitionLord;
    }

    /*
    This re-executes the last execution command (ignores refresh commands).
    This is potentially useful for IDEs to hook into (hotkey to execute last command).
     */
    public void reExecuteLastCommand() {
        ExecutionRequest executionRequest = lastExecutionRequest;
        if (executionRequest != null) {
            gradlePluginLord.addExecutionRequestToQueue(executionRequest.getFullCommandLine(), executionRequest.getDisplayName(), executionRequest.forceOutputToBeShown());
        }
    }
}
