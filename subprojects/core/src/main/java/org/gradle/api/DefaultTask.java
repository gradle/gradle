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

package org.gradle.api;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskDestroyables;
import org.gradle.api.tasks.TaskLocalState;
import org.gradle.internal.extensibility.NoConventionMapping;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * {@code DefaultTask} is the standard {@link Task} implementation. You can extend this to implement your own task types.
 */
@NoConventionMapping
@SuppressWarnings("deprecation")
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
public class DefaultTask extends org.gradle.api.internal.AbstractTask implements Task {
    // NOTE: These methods are duplicated here because Eclipse treats methods implemented in the deprecated
    // AbstractTask as also deprecated in DefaultTask.

    @Override
    public AntBuilder getAnt() {
        return super.getAnt();
    }

    @Override
    public Project getProject() {
        return super.getProject();
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public List<Action<? super Task>> getActions() {
        return super.getActions();
    }

    @Override
    public void setActions(List<Action<? super Task>> replacements) {
        super.setActions(replacements);
    }

    @Override
    public Set<Object> getDependsOn() {
        return super.getDependsOn();
    }

    @Override
    public void setDependsOn(Iterable<?> dependsOn) {
        super.setDependsOn(dependsOn);
    }

    @Override
    public void onlyIf(Spec<? super Task> spec) {
        super.onlyIf(spec);
    }

    @Override
    public void setOnlyIf(Spec<? super Task> spec) {
        super.setOnlyIf(spec);
    }

    @Override
    public boolean getDidWork() {
        return super.getDidWork();
    }

    @Override
    public void setDidWork(boolean didWork) {
        super.setDidWork(didWork);
    }

    @Override
    public boolean getEnabled() {
        return super.getEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    @Override
    public String getPath() {
        return super.getPath();
    }

    @Override
    public Task dependsOn(Object... paths) {
        return super.dependsOn(paths);
    }

    @Override
    public Task doFirst(Action<? super Task> action) {
        return super.doFirst(action);
    }

    @Override
    public Task doFirst(String actionName, Action<? super Task> action) {
        return super.doFirst(actionName, action);
    }

    @Override
    public Task doLast(Action<? super Task> action) {
        return super.doLast(action);
    }

    @Override
    public Task doLast(String actionName, Action<? super Task> action) {
        return super.doLast(actionName, action);
    }

    @Override
    public int compareTo(Task otherTask) {
        return super.compareTo(otherTask);
    }

    @Override
    public Logger getLogger() {
        return super.getLogger();
    }

    @Override
    public Object property(String propertyName) throws MissingPropertyException {
        return super.property(propertyName);
    }

    @Override
    public boolean hasProperty(String propertyName) {
        return super.hasProperty(propertyName);
    }

    @Override
    public void setProperty(String name, Object value) {
        super.setProperty(name, value);
    }

    @Override
    public String getDescription() {
        return super.getDescription();
    }

    @Override
    public void setDescription(String description) {
        super.setDescription(description);
    }

    @Override
    public String getGroup() {
        return super.getGroup();
    }

    @Override
    public void setGroup(String group) {
        super.setGroup(group);
    }

    @Override
    public TaskDestroyables getDestroyables() {
        return super.getDestroyables();
    }

    @Override
    public TaskLocalState getLocalState() {
        return super.getLocalState();
    }

    @Override
    public File getTemporaryDir() {
        return super.getTemporaryDir();
    }

    @Override
    public void setMustRunAfter(Iterable<?> mustRunAfterTasks) {
        super.setMustRunAfter(mustRunAfterTasks);
    }

    @Override
    public Task mustRunAfter(Object... paths) {
        return super.mustRunAfter(paths);
    }

    @Override
    public TaskDependency getMustRunAfter() {
        return super.getMustRunAfter();
    }

    @Override
    public void setFinalizedBy(Iterable<?> finalizedByTasks) {
        super.setFinalizedBy(finalizedByTasks);
    }

    @Override
    public Task finalizedBy(Object... paths) {
        return super.finalizedBy(paths);
    }

    @Override
    public TaskDependency getFinalizedBy() {
        return super.getFinalizedBy();
    }

    @Override
    public TaskDependency shouldRunAfter(Object... paths) {
        return super.shouldRunAfter(paths);
    }

    @Override
    public void setShouldRunAfter(Iterable<?> shouldRunAfterTasks) {
        super.setShouldRunAfter(shouldRunAfterTasks);
    }

    @Override
    public TaskDependency getShouldRunAfter() {
        return super.getShouldRunAfter();
    }

    @Override
    public Property<Duration> getTimeout() {
        return super.getTimeout();
    }

    @Override
    public void usesService(Provider<? extends BuildService<?>> service) {
        super.usesService(service);
    }

    @Override
    public TaskStateInternal getState() {
        return super.getState();
    }

    @Override
    public TaskDependencyInternal getTaskDependencies() {
        return super.getTaskDependencies();
    }

    @Override
    public void onlyIf(Closure onlyIfClosure) {
        super.onlyIf(onlyIfClosure);
    }

    @Override
    public void setOnlyIf(Closure onlyIfClosure) {
        super.setOnlyIf(onlyIfClosure);
    }

    @Override
    public org.gradle.api.logging.LoggingManager getLogging() {
        return super.getLogging();
    }

    @Override
    public TaskInputsInternal getInputs() {
        return super.getInputs();
    }

    @Override
    public TaskOutputsInternal getOutputs() {
        return super.getOutputs();
    }

    @Override
    public Task doFirst(Closure action) {
        return super.doFirst(action);
    }

    @Override
    public Task doLast(Closure action) {
        return super.doLast(action);
    }

    @Override
    public Task configure(Closure closure) {
        return super.configure(closure);
    }

    @Override
    public ExtensionContainer getExtensions() {
        return super.getExtensions();
    }
}
