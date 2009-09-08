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
package org.gradle.gradleplugin.foundation;

import org.gradle.DefaultCommandLine2StartParameterConverter;
import org.gradle.StartParameter;
import org.gradle.util.GUtil;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.GradleScriptException;
import org.gradle.foundation.ProjectView;
import org.gradle.foundation.common.ObserverLord;
import org.gradle.foundation.ipc.basic.ProcessLauncherServer;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.foundation.queue.ExecutionQueue;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;
import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.codehaus.groovy.runtime.StackTraceUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Formatter;
import java.util.Collections;

/**
 * This class has nothing to do with plugins inside of gradle, but are related
 * to making a plugin that uses gradle, such as for an IDE. It is also used by
 * the standalone IDE (that way the standalone UI and plugin UIs are kept in
 * synch).
 * <p/>
 * This is the class that stores most of the information that the Gradle plugin
 * works directly with. It is meant to simplify creating a plugin that uses
 * gradle. It maintains a queue of commands to execute and executes them in a
 * separate process due to some complexities with gradle and its dependencies
 * classpaths and potential memory issues.
 *
 * @author mhunsicker
 */
public class GradlePluginLord {
    private File gradleHomeDirectory;   //the directory where gradle is installed
    private File currentDirectory;      //the directory of your gradle-based project
    private File customGradleExecutor;  //probably will be null. This allows a user to specify a different batch file or shell script to initiate gradle.

    private List<ProjectView> projects = new ArrayList<ProjectView>();

    private FavoritesEditor favoritesEditor;  //an editor for the current favorites. The user can edit this at any time, hence we're using an editor.

    private ExecutionQueue<Request> executionQueue;

    private boolean isStarted = false;  //this flag is mostly to prevent initialization from firing off repeated refresh requests.

    private StartParameter.ShowStacktrace stackTraceLevel = StartParameter.ShowStacktrace.INTERNAL_EXCEPTIONS;
    private LogLevel logLevel = LogLevel.LIFECYCLE;

    private ObserverLord<GeneralPluginObserver> generalObserverLord = new ObserverLord<GeneralPluginObserver>();

    private ObserverLord<CommandLineArgumentAlteringListener> commandLineArgumentObserverLord = new ObserverLord<CommandLineArgumentAlteringListener>();

    public List<ProjectView> getProjects() {
        return Collections.unmodifiableList(projects);
    }

    public void setProjects(final List<ProjectView> newProjects) {
        projects.clear();
        if (newProjects != null)
            projects.addAll(newProjects);

        generalObserverLord.notifyObservers(new ObserverLord.ObserverNotification<GeneralPluginObserver>() {
            public void notify(GeneralPluginObserver observer) {
                observer.projectsAndTasksReloaded(newProjects != null);
            }
        });
    }

    public interface GeneralPluginObserver {
        /**
           Notification that we're about to reload the projects and tasks.
        */
        public void startingProjectsAndTasksReload();

        /**
           Notification that the projects and tasks have been reloaded. You may want
           to repopulate or update your views.
           @param wasSuccessful true if they were successfully reloaded. False if an
                                error occurred so we no longer can show the projects
                                and tasks (probably an error in a .gradle file).
        */
        public void projectsAndTasksReloaded(boolean wasSuccessful);
    }


    public GradlePluginLord() {
        favoritesEditor = new FavoritesEditor();

        //create the queue that executes the commands. The contents of this interaction are where we actually launch gradle.
        executionQueue = new ExecutionQueue<Request>(new ExecutionQueueInteraction());

        currentDirectory = new File(System.getProperty("user.dir"));

        String gradleHomeProperty = System.getProperty("gradle.home");
        if (gradleHomeProperty != null)
            gradleHomeDirectory = new File(gradleHomeProperty);
        else
            gradleHomeDirectory = currentDirectory;
    }


    public File getGradleHomeDirectory() {
        return gradleHomeDirectory;
    }

    //sets the gradle home directory. You can't just set this here. You must also set the "gradle.home" system property.
    //This code could do this for you, but at this time, I didn't want this to have side effects and setting "gradle.home"
    //can affect other things and there may be some timing issues.
    public void setGradleHomeDirectory(File gradleHomeDirectory) {
        this.gradleHomeDirectory = gradleHomeDirectory;
    }

    /**
       @return the root directory of your gradle project.
    */
    public File getCurrentDirectory() {
        return currentDirectory;
    }

    /**
       @param  currentDirectory the new root directory of your gradle project.
    */
    public void setCurrentDirectory(File currentDirectory) {
        this.currentDirectory = currentDirectory;
    }

    public File getCustomGradleExecutor() {
        return customGradleExecutor;
    }

    public void setCustomGradleExecutor(File customGradleExecutor) {
        this.customGradleExecutor = customGradleExecutor;
    }


    public FavoritesEditor getFavoritesEditor() {
        return favoritesEditor;
    }

    //this allows you to change how much information is given when an error occurs.
    public void setStackTraceLevel(StartParameter.ShowStacktrace stackTraceLevel) {
        this.stackTraceLevel = stackTraceLevel;
    }

    public StartParameter.ShowStacktrace getStackTraceLevel() {
        return stackTraceLevel;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        if (logLevel == null)
            return;

        this.logLevel = logLevel;
    }

    /**
       Call this to start execution. This is done after you've initialized everything.
    */
    public void startExecutionQueue() {
        isStarted = true;
    }


    /**
       This gives requests of the queue and then executes them by kicking off gradle
       in a separate process. Most of the code here is tedious setup code needed to
       start the server. The server is what starts gradle and opens a socket for
       interprocess communication so we can receive messages back from gradle.
    */
    private class ExecutionQueueInteraction implements ExecutionQueue.ExecutionInteraction<Request> {
        /**
           When this is called, execute the given request.

           @param  request    the request to execute.
        */
        public void execute(Request request) {
            //I'm just putting these in temp variables for eaiser debugging
            File currentDirectory = getCurrentDirectory();
            File gradleHomeDirectory = getGradleHomeDirectory();
            File customGradleExecutor = getCustomGradleExecutor();

            //the protocol handles the command line to launch gradle and messaging between us and said externally launched gradle.
            ProcessLauncherServer.Protocol serverProtocol = request.createServerProtocol(logLevel, stackTraceLevel, currentDirectory, gradleHomeDirectory, customGradleExecutor);

            //the server kicks off gradle as an external process and manages the communication with said process
            ProcessLauncherServer server = new ProcessLauncherServer(serverProtocol);
            request.setProcessLauncherServer(server);

            server.execute();
        }
    }


    /**
       Adds an observer for various events. See PluginObserver.

       @param  observer     your observer
       @param  inEventQueue true if you want to be notified in the Event Dispatch Thread.
    */
    public void addGeneralPluginObserver(GeneralPluginObserver observer, boolean inEventQueue) {
        generalObserverLord.addObserver(observer, inEventQueue);
    }

    public void removeGeneralPluginObserver(GeneralPluginObserver observer) {
        generalObserverLord.removeObserver(observer);
    }

    /**
       Determines if all required setup is complete based on the current settings.

       @return true if a setup is complete, false if not.
    */
    public boolean isSetupComplete() {
        //return gradleWrapper.getGradleHomeDirectory() != null &&
        //       gradleWrapper.getGradleHomeDirectory().exists() &&
        return getCurrentDirectory() != null &&
                getCurrentDirectory().exists();
    }

    public Request addExecutionRequestToQueue(String fullCommandLine, ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction) {
        if (!isStarted)
            return null;

        //here we'll give the UI a chance to add things to the command line.
        fullCommandLine = alterCommandLine(fullCommandLine);

        ExecutionRequest request = new ExecutionRequest(fullCommandLine, executionQueue, executionInteraction);
        executionQueue.addRequestToQueue(request);
        return request;
    }

    public Request addRefreshRequestToQueue(ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction) {
        if (!isStarted)
            return null;

        //we'll request a task list since there is no way to do a no op. We're not really interested
        //in what's being executed, just the ability to get the task list (which must be populated as
        //part of executing anything).
        String fullCommandLine = '-' + DefaultCommandLine2StartParameterConverter.TASKS;

        //here we'll give the UI a chance to add things to the command line.
        fullCommandLine = alterCommandLine(fullCommandLine);

        RefreshTaskListRequest request = new RefreshTaskListRequest(fullCommandLine, executionQueue, executionInteraction, this);
        executionQueue.addRequestToQueue(request);
        return request;
    }

    /**
       This is where we notify listeners and give them a chance to add things
       to the command line.

       @param  fullCommandLine the full command line
       @return the new command line.
    */
    private String alterCommandLine(String fullCommandLine) {
        CommandLineArgumentAlteringNotification notification = new CommandLineArgumentAlteringNotification(fullCommandLine);
        commandLineArgumentObserverLord.notifyObservers(notification);

        return notification.getFullCommandLine();
    }


    //
    /**
       This class notifies the listeners and modifies the command line by adding
       additional commands to it. Each listener will be given the 'new' full command
       line, so the order you add things becomes important.
    */
    private class CommandLineArgumentAlteringNotification implements ObserverLord.ObserverNotification<CommandLineArgumentAlteringListener> {
        private StringBuilder fullCommandLineBuilder;

        private CommandLineArgumentAlteringNotification(String fullCommandLine) {
            this.fullCommandLineBuilder = new StringBuilder(fullCommandLine);
        }

        public void notify(CommandLineArgumentAlteringListener observer) {
            String additions = observer.getAdditionalCommandLineArguments(fullCommandLineBuilder.toString());
            if (additions != null)
                fullCommandLineBuilder.append(' ').append(additions);
        }

        public String getFullCommandLine() {
            return fullCommandLineBuilder.toString();
        }
    }


    /**
       This allows you to add a listener that can add additional command line
       arguments whenever gradle is executed. This is useful if you've customized
       your gradle build and need to specify, for example, an init script.

       @param  listener   the listener that modifies the command line arguments.
    */
    public void addCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListener listener) {
        commandLineArgumentObserverLord.addObserver(listener, false);
    }

    public void removeCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListener listener) {
        commandLineArgumentObserverLord.removeObserver(listener);
    }

    //this code was copied from BuildExceptionReporter.reportBuildFailure in gradle's source, then modified slightly
    //to compensate for the fact that we're not driven by options or logging things to a logger object.
    public static String getGradleExceptionMessage(Throwable failure, StartParameter.ShowStacktrace stackTraceLevel) {
        if (failure == null)
            return "";

        Formatter formatter = new Formatter();

        formatter.format("%nBuild failed.%n");

        if (stackTraceLevel == StartParameter.ShowStacktrace.INTERNAL_EXCEPTIONS)
            formatter.format("Use the stack trace options to get more details.");

        if (failure != null) {
            formatter.format("%n");

            if (failure instanceof GradleScriptException) {
                GradleScriptException scriptException = ((GradleScriptException) failure).getReportableException();
                formatter.format("%s%n%n", scriptException.getLocation());

                formatter.format("%s%nCause: %s", scriptException.getOriginalMessage(), getMessage(
                        scriptException.getCause()));
            } else {
                formatter.format("%s", getMessage(failure));
            }

            if (stackTraceLevel != StartParameter.ShowStacktrace.INTERNAL_EXCEPTIONS) {
                formatter.format("%n%nException is:\n");
                if (stackTraceLevel == StartParameter.ShowStacktrace.ALWAYS_FULL)
                    return formatter.toString() + getStackTraceAsText(failure);

                return formatter.toString() + getStackTraceAsText(StackTraceUtils.deepSanitize(failure));
            }
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
        if (!GUtil.isTrue(message))
            message = String.format("%s (no error message)", throwable.getClass().getName());

        if (throwable.getCause() != null)
            message += "\nCaused by: " + getMessage(throwable.getCause());

        return message;
    }

}
