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

import groovy.util.AntBuilder;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.*;
import org.gradle.api.plugins.Convention;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.DefaultStandardOutputCapture;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Hans Dockter
 */
public abstract class AbstractTask implements TaskInternal {
    private static Logger logger = LoggerFactory.getLogger(AbstractTask.class);
    private static Logger buildLogger = LoggerFactory.getLogger(Task.class);

    private ProjectInternal project;

    private String name;

    private List<TaskAction> actions = new ArrayList<TaskAction>();

    private List<String> skipProperties = new ArrayList<String>();

    private boolean executing;

    private boolean executed;

    private boolean enabled = true;

    private String path = null;

    private StandardOutputCapture standardOutputCapture = new DefaultStandardOutputCapture(true, LogLevel.QUIET);

    private DefaultTaskDependency dependencies = new DefaultTaskDependency();

    private DynamicObjectHelper dynamicObjectHelper;

    private String description;

    protected AbstractTask() {
        dynamicObjectHelper = new DynamicObjectHelper(this);
        dynamicObjectHelper.setConvention(new DefaultConvention());
    }

    public AbstractTask(Project project, String name) {
        assert project != null;
        assert name != null;
        this.project = (ProjectInternal) project;
        this.name = name;
        path = project.absolutePath(name);
        dynamicObjectHelper = new DynamicObjectHelper(this);
        dynamicObjectHelper.setConvention(new DefaultConvention());
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

    public List<TaskAction> getActions() {
        return actions;
    }

    public void setActions(List<TaskAction> actions) {
        this.actions = actions;
    }

    public List<String> getSkipProperties() {
        return skipProperties;
    }

    public void setSkipProperties(List<String> skipProperties) {
        this.skipProperties = skipProperties;
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

    public boolean isExecuted() {
        return executed;
    }

    public boolean getExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
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

    public Task deleteAllActions() {
        actions = new ArrayList<TaskAction>();
        return this;
    }

    public void execute() {
        executing = true;
        logger.debug("Starting to execute Task: {}", path);
        if (!isSkipped()) {
            logger.info(Logging.LIFECYCLE, "{}", path);
            standardOutputCapture.start();
            for (TaskAction action : actions) {
                logger.debug("Executing Action:");
                try {
                    doExecute(action);
                } catch (StopExecutionException e) {
                    logger.info("Execution stopped by some action with message: {}", e.getMessage());
                    break;
                } catch (StopActionException e) {
                    logger.debug("Action stopped by some action with message: {}", e.getMessage());
                    continue;
                } catch (Throwable t) {
                    executing = false;
                    standardOutputCapture.stop();
                    throw new GradleScriptException(String.format("Execution failed for %s.", this), t, project.getBuildScriptSource());
                }
            }
            standardOutputCapture.stop();
        }
        executing = false;
        executed = true;
        logger.debug("Finished executing Task: {}", path);
    }

    private boolean isSkipped() {
        List trueSkips = new ArrayList();
        if (enabled) {
            List<String> allSkipProperties = new ArrayList<String>(skipProperties);
            allSkipProperties.add(Task.AUTOSKIP_PROPERTY_PREFIX + name);
            for (String skipProperty : allSkipProperties) {
                String propValue = System.getProperty(skipProperty);
                if (propValue != null && !(propValue.toUpperCase().equals("FALSE"))) {
                    trueSkips.add(skipProperty);
                }
            }
            if (trueSkips.size() > 0) {
                logger.info("Skipping execution as following skip properties are true: " + trueSkips);
            }
        } else {
            logger.info("Skipping execution as task is disabled.");
        }
        if (!enabled || trueSkips.size() > 0) {
            logger.info(Logging.LIFECYCLE, "{} SKIPPED", path);
            return true;
        }
        return false;
    }

    private void doExecute(TaskAction action) throws Throwable {
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

    public Task doFirst(TaskAction action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        actions.add(0, action);
        return this;
    }

    public Task doLast(TaskAction action) {
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

    public void defineProperty(String name, Object value) {
        dynamicObjectHelper.setProperty(name, value);
    }

    public Convention getConvention() {
        return dynamicObjectHelper.getConvention();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
