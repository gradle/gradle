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
package org.gradle.gradleplugin.foundation;

import org.codehaus.groovy.runtime.StackTraceUtils;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.foundation.CommandLineAssistant;
import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;
import org.gradle.foundation.common.ObserverLord;
import org.gradle.foundation.ipc.basic.ProcessLauncherServer;
import org.gradle.foundation.queue.ExecutionQueue;
import org.gradle.gradleplugin.foundation.favorites.FavoriteTask;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;
import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

/**
 * This class has nothing to do with plugins inside of gradle, but are related to making a plugin that uses gradle, such as for an IDE. It is also used by the standalone IDE (that way the standalone
 * UI and plugin UIs are kept in synch). <p/> This is the class that stores most of the information that the Gradle plugin works directly with. It is meant to simplify creating a plugin that uses
 * gradle. It maintains a queue of commands to execute and executes them in a separate process due to some complexities with gradle and its dependencies classpaths and potential memory issues.
 */
public class GradlePluginLord {
    private final Logger logger = Logging.getLogger(GradlePluginLord.class);

    private File gradleHomeDirectory;   //the directory where gradle is installed
    private File currentDirectory;      //the directory of your gradle-based project
    private File customGradleExecutor;  //probably will be null. This allows a user to specify a different batch file or shell script to initiate gradle.

    private List<ProjectView> projects = new ArrayList<ProjectView>();

    private FavoritesEditor favoritesEditor;  //an editor for the current favorites. The user can edit this at any time, hence we're using an editor.

    private QueueManager queueManager = new QueueManager();

    private ShowStacktrace stackTraceLevel = ShowStacktrace.INTERNAL_EXCEPTIONS;
    private LogLevel logLevel = LogLevel.LIFECYCLE;

    private ObserverLord<GeneralPluginObserver> generalObserverLord = new ObserverLord<GeneralPluginObserver>();
    private ObserverLord<RequestObserver> requestObserverLord = new ObserverLord<RequestObserver>();
    private ObserverLord<SettingsObserver> settingsObserverLord = new ObserverLord<SettingsObserver>();

    private ObserverLord<CommandLineArgumentAlteringListener> commandLineArgumentObserverLord = new ObserverLord<CommandLineArgumentAlteringListener>();

    private long nextRequestID = 1;  //a unique number assigned to requests

    public List<ProjectView> getProjects() {
        return Collections.unmodifiableList(projects);
    }

    /**
     * Sets the current projects. This is only supposed to be called by internal gradle classes.
     */
    public void setProjects(final List<ProjectView> newProjects) {
        projects.clear();
        if (newProjects != null) {
            projects.addAll(newProjects);
        }

        generalObserverLord.notifyObservers(new ObserverLord.ObserverNotification<GeneralPluginObserver>() {
            public void notify(GeneralPluginObserver observer) {
                observer.projectsAndTasksReloaded(newProjects != null);
            }
        });
    }

    public interface GeneralPluginObserver {
        /**
         * Notification that we're about to reload the projects and tasks.
         */
        void startingProjectsAndTasksReload();

        /**
         * Notification that the projects and tasks have been reloaded. You may want to repopulate or update your views.
         *
         * @param wasSuccessful true if they were successfully reloaded. False if an error occurred so we no longer can show the projects and tasks (probably an error in a .gradle file).
         */
        void projectsAndTasksReloaded(boolean wasSuccessful);
    }

    public interface RequestObserver {
        void executionRequestAdded(ExecutionRequest request);

        void refreshRequestAdded(RefreshTaskListRequest request);

        /**
         * Notification that a command is about to be executed. This is mostly useful for IDE's that may need to save their files.
         */
        void aboutToExecuteRequest(Request request);

        /**
         * Notification that the command has completed execution.
         *
         * @param request the original request containing the command that was executed
         * @param result the result of the command
         * @param output the output from gradle executing the command
         */
        void requestExecutionComplete(Request request, int result, String output);
    }

    public interface SettingsObserver {

        /**
         * Notification that some settings have changed for the plugin. Settings such as current directory, gradle home directory, etc. This is useful for UIs that need to update their UIs when this
         * is changed by other means.
         */
        void settingsChanged();
    }

    public GradlePluginLord() {
        favoritesEditor = new FavoritesEditor();

        //create the queue that executes the commands. The contents of this interaction are where we actually launch gradle.

        currentDirectory = SystemProperties.getInstance().getCurrentDir();

        String gradleHomeProperty = System.getProperty("gradle.home");
        if (gradleHomeProperty != null) {
            gradleHomeDirectory = new File(gradleHomeProperty);
        } else {
            GradleInstallation gradleInstallation = CurrentGradleInstallation.get();
            gradleHomeDirectory = gradleInstallation == null ? null : gradleInstallation.getGradleHome();
        }
    }

    public File getGradleHomeDirectory() {
        if (gradleHomeDirectory == null || !gradleHomeDirectory.isDirectory()) {
            throw new IllegalArgumentException("Could not locate Gradle home directory.");
        }
        return gradleHomeDirectory;
    }

    /**
     * sets the gradle home directory. You can't just set this here. You must also set the "gradle.home" system property. This code could do this for you, but at this time, I didn't want this to have
     * side effects and setting "gradle.home" can affect other things and there may be some timing issues.
     *
     * @param gradleHomeDirectory the new home directory
     */
    public void setGradleHomeDirectory(File gradleHomeDirectory) {
        if (areEqual(this.gradleHomeDirectory, gradleHomeDirectory))    //already set to this. This prevents recursive notifications.
        {
            return;
        }
        this.gradleHomeDirectory = gradleHomeDirectory;
        notifySettingsChanged();
    }

    /**
     * @return the root directory of your gradle project.
     */
    public File getCurrentDirectory() {
        return currentDirectory;
    }

    /**
     * @param currentDirectory the new root directory of your gradle project.
     * @returns true if we changed the current directory, false if not (it was already set to this)
     */
    public boolean setCurrentDirectory(File currentDirectory) {
        if (areEqual(this.currentDirectory, currentDirectory))    //already set to this. This prevents recursive notifications.
        {
            return false;
        }
        this.currentDirectory = currentDirectory;
        notifySettingsChanged();
        return true;
    }

    public File getCustomGradleExecutor() {
        return customGradleExecutor;
    }

    public boolean setCustomGradleExecutor(File customGradleExecutor) {
        if (areEqual(this.customGradleExecutor, customGradleExecutor))    //already set to this. This prevents recursive notifications.
        {
            return false;
        }
        this.customGradleExecutor = customGradleExecutor;
        notifySettingsChanged();
        return true;
    }

    public FavoritesEditor getFavoritesEditor() {
        return favoritesEditor;
    }

    /**
     * this allows you to change how much information is given when an error occurs.
     */
    public void setStackTraceLevel(ShowStacktrace stackTraceLevel) {
        if (areEqual(this.stackTraceLevel, stackTraceLevel))    //already set to this. This prevents recursive notifications.
        {
            return;
        }
        this.stackTraceLevel = stackTraceLevel;
        notifySettingsChanged();
    }

    public ShowStacktrace getStackTraceLevel() {
        return stackTraceLevel;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        if (logLevel == null) {
            return;
        }

        if (areEqual(this.logLevel, logLevel))    //already set to this. This prevents recursive notifications.
        {
            return;
        }
        this.logLevel = logLevel;
        notifySettingsChanged();
    }

    /**
     * This gives requests of the queue and then executes them by kicking off gradle in a separate process. Most of the code here is tedious setup code needed to start the server. The server is what
     * starts gradle and opens a socket for interprocess communication so we can receive messages back from gradle.
     */
    private class ExecutionQueueInteraction implements ExecutionQueue.ExecutionInteraction<Request> {
        /**
         * When this is called, execute the given request.
         *
         * @param request the request to execute.
         */
        public void execute(final Request request) {
            notifyAboutToExecuteRequest(request);

            //I'm just putting these in temp variables for easier debugging
            File currentDirectory = getCurrentDirectory();
            File gradleHomeDirectory = getGradleHomeDirectory();
            File customGradleExecutor = getCustomGradleExecutor();

            //the protocol handles the command line to launch gradle and messaging between us and said externally launched gradle.
            ProcessLauncherServer.Protocol serverProtocol = request.createServerProtocol(logLevel, stackTraceLevel, currentDirectory, gradleHomeDirectory, customGradleExecutor);

            //the server kicks off gradle as an external process and manages the communication with said process
            ProcessLauncherServer server = new ProcessLauncherServer(serverProtocol);
            request.setProcessLauncherServer(server);

            //we need to know when this command is finished executing so we can mark it as complete and notify any observers.
            server.addServerObserver(new ProcessLauncherServer.ServerObserver() {
                public void clientExited(int result, String output) {
                    queueManager.onComplete(request);
                    notifyRequestExecutionComplete(request, result, output);
                }

                public void serverExited() {
                }
            }, false);

            server.start();
        }
    }

    private void notifyAboutToExecuteRequest(final Request request) {
        requestObserverLord.notifyObservers(new ObserverLord.ObserverNotification<RequestObserver>() {
            public void notify(RequestObserver observer) {
                try { //wrap this in a try/catch block so exceptions in the observer doesn't stop everything
                    observer.aboutToExecuteRequest(request);
                } catch (Exception e) {
                    logger.error("notifying aboutToExecuteCommand() " + e.getMessage());
                }
            }
        });
    }

    private void notifyRequestExecutionComplete(final Request request, final int result, final String output) {
        requestObserverLord.notifyObservers(new ObserverLord.ObserverNotification<RequestObserver>() {
            public void notify(RequestObserver observer) {
                try { //wrap this in a try/catch block so exceptions in the observer doesn't stop everything
                    observer.requestExecutionComplete(request, result, output);
                } catch (Exception e) {
                    logger.error("notifying requestExecutionComplete() " + e.getMessage());
                }
            }
        });
    }

    /**
     * Adds an observer for various events. See PluginObserver.
     *
     * @param observer your observer
     * @param inEventQueue true if you want to be notified in the Event Dispatch Thread.
     */
    public void addGeneralPluginObserver(GeneralPluginObserver observer, boolean inEventQueue) {
        generalObserverLord.addObserver(observer, inEventQueue);
    }

    public void removeGeneralPluginObserver(GeneralPluginObserver observer) {
        generalObserverLord.removeObserver(observer);
    }

    public void addRequestObserver(RequestObserver observer, boolean inEventQueue) {
        requestObserverLord.addObserver(observer, inEventQueue);
    }

    public void removeRequestObserver(RequestObserver observer) {
        requestObserverLord.removeObserver(observer);
    }

    public void addSettingsObserver(SettingsObserver observer, boolean inEventQueue) {
        settingsObserverLord.addObserver(observer, inEventQueue);
    }

    public void removeSettingsObserver(SettingsObserver observer) {
        settingsObserverLord.removeObserver(observer);
    }

    private void notifySettingsChanged() {
        settingsObserverLord.notifyObservers(new ObserverLord.ObserverNotification<SettingsObserver>() {
            public void notify(SettingsObserver observer) {
                observer.settingsChanged();
            }
        });
    }

    /**
     * Determines if two are objects are equal and considers them both being null as equal
     *
     * @param object1 the first object
     * @param object2 the second object
     * @return true if they're both null or both equal.
     */
    private boolean areEqual(Object object1, Object object2) {
        if (object1 == null || object2 == null) {
            return object2 == object1; //yes, we're not using '.equals', we're making sure they both equal null because one of them is null!
        }

        return object1.equals(object2);
    }

    /**
     * Determines if all required setup is complete based on the current settings.
     *
     * @return true if a setup is complete, false if not.
     */
    public boolean isSetupComplete() {
        //return gradleWrapper.getGradleHomeDirectory() != null &&
        //       gradleWrapper.getGradleHomeDirectory().exists() &&
        return getCurrentDirectory() != null && getCurrentDirectory().exists();
    }

    public Request addExecutionRequestToQueue(String fullCommandLine, String displayName) {
        return addExecutionRequestToQueue(fullCommandLine, displayName, false);
    }

    /**
     * This executes a task in a background thread. This creates or uses an existing OutputPanel to display the results.
     *
     * @param task the task to execute.
     * @param forceOutputToBeShown overrides the user setting onlyShowOutputOnErrors so that the output is shown regardless
     */
    public Request addExecutionRequestToQueue(final TaskView task, boolean forceOutputToBeShown, String... additionCommandLineOptions) {
        if (task == null) {
            return null;
        }

        String fullCommandLine = CommandLineAssistant.appendAdditionalCommandLineOptions(task, additionCommandLineOptions);
        return addExecutionRequestToQueue(fullCommandLine, task.getFullTaskName(), forceOutputToBeShown);
    }

    /**
     * Executes several favorites commands at once as a single command. This has the affect of simply concatenating all the favorite command lines into a single line.
     *
     * @param favorites a list of favorites. If just one favorite, it executes it normally. If multiple favorites, it executes them all at once as a single command.
     */
    public Request addExecutionRequestToQueue(List<FavoriteTask> favorites) {
        if (favorites.isEmpty()) {
            return null;
        }

        FavoriteTask firstFavoriteTask = favorites.get(0);
        String displayName;
        String fullCommandLine;
        boolean alwaysShowOutput = firstFavoriteTask.alwaysShowOutput();

        if (favorites.size() == 1) {
            displayName = firstFavoriteTask.getDisplayName();
            fullCommandLine = firstFavoriteTask.getFullCommandLine();
        } else {
            displayName = "Multiple (" + firstFavoriteTask.getDisplayName() + ", ... )";
            fullCommandLine = FavoritesEditor.combineFavoriteCommandLines(favorites);
        }

        return addExecutionRequestToQueue(fullCommandLine, displayName, alwaysShowOutput);
    }

    /**
     * Call this to execute a task in a background thread. This creates or uses an existing OutputPanel to display the results. This version takes text instead of a task object.
     *
     * @param fullCommandLine the full command line to pass to gradle.
     * @param displayName what we show on the tab.
     * @param forceOutputToBeShown overrides the user setting onlyShowOutputOnErrors so that the output is shown regardless
     */
    public Request addExecutionRequestToQueue(String fullCommandLine, String displayName, boolean forceOutputToBeShown) {
        if (fullCommandLine == null) {
            return null;
        }

        //here we'll give the UI a chance to add things to the command line.
        fullCommandLine = alterCommandLine(fullCommandLine);

        final ExecutionRequest request = new ExecutionRequest(getNextRequestID(), fullCommandLine, displayName, forceOutputToBeShown, queueManager);
        requestObserverLord.notifyObservers(new ObserverLord.ObserverNotification<RequestObserver>() {
            public void notify(RequestObserver observer) {
                observer.executionRequestAdded(request);
            }
        });
        queueManager.addRequestToQueue(request);
        return request;
    }

    private synchronized long getNextRequestID() {
        return nextRequestID++;
    }

    /**
     * This will refresh the project/task tree.
     *
     * @return the Request that was created. Null if no request created.
     */
    public Request addRefreshRequestToQueue() {
        return addRefreshRequestToQueue(null);
    }

    /**
     * This will refresh the project/task tree. This version allows you to specify additional arguments to be passed to gradle during the refresh (such as -b to specify a build file)
     *
     * @param additionalCommandLineArguments the arguments to add, or null if none.
     * @return the Request that was created.
     */
    public Request addRefreshRequestToQueue(String additionalCommandLineArguments) {
        //we'll request a task list since there is no way to do a no op. We're not really interested
        //in what's being executed, just the ability to get the task list (which must be populated as
        //part of executing anything).
        String fullCommandLine = ProjectInternal.TASKS_TASK;

        if (additionalCommandLineArguments != null) {
            fullCommandLine += ' ' + additionalCommandLineArguments;
        }

        //here we'll give the UI a chance to add things to the command line.
        fullCommandLine = alterCommandLine(fullCommandLine);

        // Don't schedule again if already doing a refresh with the specified arguments
        // TODO - fix this race condition - multiple threads may be requesting a refresh
        List<Request> currentRequests = queueManager.findRequestsOfType(RefreshTaskListRequest.TYPE);
        for (Request currentRequest : currentRequests) {
            if (currentRequest.getFullCommandLine().equals(fullCommandLine)) {
                return currentRequest;
            }
        }

        final RefreshTaskListRequest request = new RefreshTaskListRequest(getNextRequestID(), fullCommandLine, queueManager, this);
        queueManager.addRequestToQueue(request);
        // TODO - fix this race condition - request may already have completed
        requestObserverLord.notifyObservers(new ObserverLord.ObserverNotification<RequestObserver>() {
            public void notify(RequestObserver observer) {
                observer.refreshRequestAdded(request);
            }
        });
        return request;
    }

    /**
     * This is where we notify listeners and give them a chance to add things to the command line.
     *
     * @param fullCommandLine the full command line
     * @return the new command line.
     */
    private String alterCommandLine(String fullCommandLine) {
        CommandLineArgumentAlteringNotification notification = new CommandLineArgumentAlteringNotification(fullCommandLine);
        commandLineArgumentObserverLord.notifyObservers(notification);

        return notification.getFullCommandLine();
    }

    //

    /**
     * This class notifies the listeners and modifies the command line by adding additional commands to it. Each listener will be given the 'new' full command line, so the order you add things becomes
     * important.
     */
    private class CommandLineArgumentAlteringNotification implements ObserverLord.ObserverNotification<CommandLineArgumentAlteringListener> {
        private StringBuilder fullCommandLineBuilder;

        private CommandLineArgumentAlteringNotification(String fullCommandLine) {
            this.fullCommandLineBuilder = new StringBuilder(fullCommandLine);
        }

        public void notify(CommandLineArgumentAlteringListener observer) {
            String additions = observer.getAdditionalCommandLineArguments(fullCommandLineBuilder.toString());
            if (additions != null) {
                fullCommandLineBuilder.append(' ').append(additions);
            }
        }

        public String getFullCommandLine() {
            return fullCommandLineBuilder.toString();
        }
    }

    /**
     * This allows you to add a listener that can add additional command line arguments whenever gradle is executed. This is useful if you've customized your gradle build and need to specify, for
     * example, an init script.
     *
     * param  listener   the listener that modifies the command line arguments.
     */
    public void addCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListener listener) {
        commandLineArgumentObserverLord.addObserver(listener, false);
    }

    public void removeCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListener listener) {
        commandLineArgumentObserverLord.removeObserver(listener);
    }

    /**
     * This code was copied from BuildExceptionReporter.reportBuildFailure in gradle's source, then modified slightly to compensate for the fact that we're not driven by options or logging things to a
     * logger object.
     */
    public static String getGradleExceptionMessage(Throwable failure, ShowStacktrace stackTraceLevel) {
        if (failure == null) {
            return "";
        }

        Formatter formatter = new Formatter();

        formatter.format("%nBuild failed.%n");

        if (stackTraceLevel == ShowStacktrace.INTERNAL_EXCEPTIONS) {
            formatter.format("Use the stack trace options to get more details.");
        }

        formatter.format("%n");

        if (failure instanceof LocationAwareException) {
            LocationAwareException scriptException = (LocationAwareException) failure;
            formatter.format("%s%n%n", scriptException.getLocation());
            formatter.format("%s", scriptException.getCause().getMessage());

            for (Throwable cause : scriptException.getReportableCauses()) {
                formatter.format("%nCause: %s", getMessage(cause));
            }
        } else {
            formatter.format("%s", getMessage(failure));
        }

        if (stackTraceLevel != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            formatter.format("%n%nException is:\n");
            if (stackTraceLevel == ShowStacktrace.ALWAYS_FULL) {
                return formatter.toString() + getStackTraceAsText(failure);
            }

            return formatter.toString() + getStackTraceAsText(StackTraceUtils.deepSanitize(failure));
        }

        return formatter.toString();
    }

    private static String getStackTraceAsText(Throwable t) {
        StringBuilder builder = new StringBuilder();
        StackTraceElement[] stackTraceElements = t.getStackTrace();

        for (int index = 0; index < stackTraceElements.length; index++) {
            StackTraceElement stackTraceElement = stackTraceElements[index];
            builder.append("   ").append(stackTraceElement.toString()).append('\n');
        }

        return builder.toString();
    }

    //tries to get a message from a Throwable. Something there's a message and sometimes there's not.
    private static String getMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (!GUtil.isTrue(message)) {
            message = String.format("%s (no error message)", throwable.getClass().getName());
        }

        if (throwable.getCause() != null) {
            message += "\nCaused by: " + getMessage(throwable.getCause());
        }

        return message;
    }

    /**
     * Determines if there are tasks executing or waiting to execute. We only care about execution requests, not refresh requests.
     *
     * @return true if this is busy, false if not.
     */
    public boolean isBusy() {
        return !queueManager.findRequestsOfType(ExecutionRequest.TYPE).isEmpty();
    }

    private class QueueManager implements ExecutionQueue.RequestCancellation {
        private final Object lock = new Object();
        private final ExecutionQueue<Request> executionQueue = new ExecutionQueue<Request>(new ExecutionQueueInteraction());
        private final Set<Request> currentlyExecutingRequests = new HashSet<Request>();

        private List<Request> findRequestsOfType(Request.Type type) {
            List<Request> requests = new ArrayList<Request>();
            synchronized (lock) {
                for (Request request : currentlyExecutingRequests) {
                    if (request.getType() == type) {
                        requests.add(request);
                    }
                }
            }
            return requests;
        }

        public void onCancel(ExecutionQueue.Request request) {
            executionQueue.removeRequestFromQueue(request);
            synchronized (lock) {
                currentlyExecutingRequests.remove(request);
            }
        }

        public void onComplete(Request request) {
            synchronized (lock) {
                currentlyExecutingRequests.remove(request);
            }
        }

        public void addRequestToQueue(Request request) {
            synchronized (lock) {
                currentlyExecutingRequests.add(request);
            }
            executionQueue.addRequestToQueue(request);
        }
    }
}
