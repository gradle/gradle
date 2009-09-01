/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import groovy.util.AntBuilder;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.*;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.DynamicObjectAware;
import org.gradle.api.logging.*;
import org.gradle.api.plugins.Convention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.execution.OutputHandler;
import org.gradle.execution.DefaultOutputHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public abstract class AbstractTask implements TaskInternal, DynamicObjectAware {
    private static Logger logger = Logging.getLogger(AbstractTask.class);
    private static Logger buildLogger = Logging.getLogger(Task.class);
    private static ThreadLocal<TaskInfo> nextInstance = new ThreadLocal<TaskInfo>();
    private ProjectInternal project;

    private String name;

    private List<Action<? super Task>> actions = new ArrayList<Action<? super Task>>();

    private boolean executing;

    private boolean executed;

   // will be set to a default value of true before task is executed.  Derived classes may set to a more useful value
    private boolean didWork = false;

    private boolean enabled = true;

    private String path = null;

    private StandardOutputCapture standardOutputCapture = new DefaultStandardOutputCapture(true, LogLevel.QUIET);

    private DefaultTaskDependency dependencies = new DefaultTaskDependency();

    private DynamicObjectHelper dynamicObjectHelper;

    private String description;

    private Spec<? super Task> onlyIfSpec;

    private OutputHandler outputHandler;

    protected AbstractTask() {
        this(taskInfo());
    }

    private static TaskInfo taskInfo() {
        TaskInfo taskInfo = nextInstance.get();
        return taskInfo == null ? new TaskInfo(null, null) : taskInfo;
    }

    private AbstractTask(TaskInfo taskInfo) {
        this(taskInfo.project, taskInfo.name);
    }

    @Deprecated
    protected AbstractTask(Project project, String name) {
        nextInstance.set(null);
        this.project = (ProjectInternal) project;
        this.name = name;
        path = project == null ? null : project.absolutePath(name);
        dynamicObjectHelper = new DynamicObjectHelper(this, new DefaultConvention());
        outputHandler = new DefaultOutputHandler(this);
    }

    public static void injectIntoNextInstance(Project project, String name) {
        if (project != null || name != null) {
            nextInstance.set(new TaskInfo(project, name));
        } else {
            nextInstance.set(null);
        }
    }
    
    public AntBuilder getAnt() {
        return project.getAnt();
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = (ProjectInternal) project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Action<? super Task>> getActions() {
        return actions;
    }

    public void setActions(List<Action<? super Task>> actions) {
        this.actions = actions;
    }

    public TaskDependency getTaskDependencies() {
        return dependencies;
    }

    public Set<Object> getDependsOn() {
        return dependencies.getValues();
    }

    public void setDependsOn(Set<?> dependsOn) {
        dependencies.setValues(dependsOn);
    }

    public void onlyIf(Closure onlyIfClosure) {
        this.onlyIfSpec = (Spec<Task>) DefaultGroovyMethods.asType(onlyIfClosure, Spec.class);
    }

    public void onlyIf(Spec<? super Task> onlyIfSpec) {
        this.onlyIfSpec = onlyIfSpec;
    }

    public OutputHandler getOutput() {
        return outputHandler;
    }

    public boolean isExecuted() {
        return executed;
    }

    public boolean getExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public boolean isDidWork() {
        return didWork;
    }

    public boolean getDidWork() {
        return isDidWork();
    }

    public void setDidWork(boolean didWork) {
        this.didWork = didWork;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setOutputHandler(OutputHandler outputHandler) {
        this.outputHandler = outputHandler;
    }

    public Task deleteAllActions() {
        actions.clear();
        return this;
    }

    public void execute() {
        executing = true;
        logger.debug("Starting to execute Task: {}", path);
        if (!isSkipped()) {
            if (onlyIfSpec == null ||
                    project.getGradle().getStartParameter().isNoOpt() ||
                    onlyIfSpec.isSatisfiedBy(this)) {
                logger.lifecycle(path);
                didWork = true;   // assume true unless changed during execution
                standardOutputCapture.start();
                for (Action<? super Task> action : actions) {
                    logger.debug("Executing Action:");
                    try {
                        doExecute(action);
                    } catch (StopExecutionException e) {
                        logger.info("Execution stopped by some action with message: {}", e.getMessage());
                        break;
                    } catch (StopActionException e) {
                        logger.debug("Action stopped by some action with message: {}", e.getMessage());
                    } catch (Throwable t) {
                        executing = false;
                        standardOutputCapture.stop();
                        outputHandler.writeHistory(false);
                        throw new GradleScriptException(String.format("Execution failed for %s.", this), t, project.getBuildScriptSource());
                    }
                }
                outputHandler.writeHistory(true);
                standardOutputCapture.stop();
            } else {
                logger.lifecycle("{} SKIPPED as onlyIf is false", path);
            }
        }
        executing = false;
        executed = true;
        logger.debug("Finished executing Task: {}", path);
    }

    private boolean isSkipped() {
        if (!enabled) {
            logger.info("Skipping execution as task is disabled.");
            logger.lifecycle("{} SKIPPED", path);
            return true;
        }
        return false;
    }

    private void doExecute(Action<? super Task> action) throws Throwable {
        try {
            action.execute(this);
            // todo Due to a Groovy bug which wraps Exceptions from Java classes into InvokerInvocationExceptions we have to do this. After the Groovy bug is fixed we can remove this.
        } catch (InvokerInvocationException e) {
            if (e.getCause() != null) {
                throw e.getCause();
            }
            throw e;
        }
    }

    public Task dependsOn(Object... paths) {
        dependencies.add(paths);
        return this;
    }

    public Task doFirst(Action<? super Task> action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        actions.add(0, action);
        return this;
    }

    public Task doLast(Action<? super Task> action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        actions.add(action);
        return this;
    }

    public boolean equals(Object other) {
        AbstractTask otherTask = (AbstractTask) other;
        return getPath().equals(otherTask.getPath());
    }

    public int hashCode() {
        return path.hashCode();
    }

    public int compareTo(Task otherTask) {
        int depthCompare = project.compareTo(otherTask.getProject());
        if (depthCompare == 0) {
            return getPath().compareTo(otherTask.getPath());
        } else {
            return depthCompare;
        }
    }

    public String toString() {
        return String.format("task '%s'", path);
    }

    public Logger getLogger() {
        return buildLogger;
    }

    public Task disableStandardOutputCapture() {
        throwExceptionDuringExecutionTime("captureStandardOutput");
        standardOutputCapture = new DefaultStandardOutputCapture();
        return this;
    }

    public Task captureStandardOutput(LogLevel level) {
        throwExceptionDuringExecutionTime("captureStandardOutput");
        standardOutputCapture = new DefaultStandardOutputCapture(true, level);
        return this;
    }

    public StandardOutputCapture getStandardOutputCapture() {
        return standardOutputCapture;
    }

    public void setStandardOutputCapture(StandardOutputCapture standardOutputCapture) {
        this.standardOutputCapture = standardOutputCapture;
    }

    private void throwExceptionDuringExecutionTime(String operation) {
        if (executing) {
            throw new IllegalOperationAtExecutionTimeException("The operation " + operation + " must not be called at execution time.");
        }
    }

    public Map<String, Object> getAdditionalProperties() {
        return dynamicObjectHelper.getAdditionalProperties();
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        dynamicObjectHelper.setAdditionalProperties(additionalProperties);
    }

    public DynamicObjectHelper getDynamicObjectHelper() {
        return dynamicObjectHelper;
    }

    public Object property(String propertyName) throws MissingPropertyException {
        return dynamicObjectHelper.getProperty(propertyName);
    }

    public boolean hasProperty(String propertyName) {
        return dynamicObjectHelper.hasProperty(propertyName);
    }

    public void setProperty(String name, Object value) {
        dynamicObjectHelper.setProperty(name, value);
    }

    protected void defineProperty(String name, Object value) {
        dynamicObjectHelper.setProperty(name, value);
    }

    public Convention getConvention() {
        return dynamicObjectHelper.getConvention();
    }

    public void setConvention(Convention convention) {
        dynamicObjectHelper.setConvention(convention);
    }

    public DynamicObject getAsDynamicObject() {
        return dynamicObjectHelper;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean dependsOnTaskDidWork() {
        TaskDependency dependency = getTaskDependencies();
        for (Task depTask : dependency.getDependencies(this)) {
            if (depTask.getDidWork()) {
                return true;
            }
        }
        return false;
    }

    private static class TaskInfo {
        private final Project project;
        private final String name;

        private TaskInfo(Project project, String name) {
            this.name = name;
            this.project = project;
        }
    }
}
