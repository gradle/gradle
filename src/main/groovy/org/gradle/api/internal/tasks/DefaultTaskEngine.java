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
package org.gradle.api.internal.tasks;

import org.gradle.api.internal.ConfigurableObjectCollection;
import org.gradle.api.internal.AbstractDynamicObject;
import org.gradle.api.internal.project.ITaskFactory;
import org.gradle.api.*;

import java.util.*;

import groovy.lang.MissingPropertyException;
import groovy.lang.MissingMethodException;

/**
 * @author Hans Dockter
 */
public class DefaultTaskEngine extends AbstractDynamicObject implements TaskEngine {
    private List<Rule> rules = new ArrayList<Rule>();
    private ConfigurableObjectCollection<Task> tasks = new ConfigurableObjectCollection<Task>(toString());    

    public Task findTask(String name) {
        if (hasProperty(name)) {
            return getProperty(name);
        };
        return null;
    }

    public Rule addRule(Rule rule) {
        if (rule == null) {
            throw new InvalidUserDataException("Synthetic task must not be null!");
        }
        rules.add(rule);
        return rule;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void addTask(Task task) {
        tasks.put(task.getName(), task);
    }

    public ConfigurableObjectCollection<Task> getTasks() {
        return tasks;
    }

    public boolean hasProperty(String name) {
        applyRulesIfUnknownProperty(name);
        return tasks.hasProperty(name);
    }

    private void applyRulesIfUnknownProperty(String name) {
        if (!tasks.hasProperty(name)) {
            applyRules(name);
        }
    }

    private void applyRules(String name) {
        for (Rule rule : rules) {
            rule.apply(name);
        };
    }

    public Task getProperty(String name) throws MissingPropertyException {
        applyRulesIfUnknownProperty(name);
        return tasks.getProperty(name);
    }

    public Map<String, Task> getProperties() {
        return tasks.getProperties();
    }

    public boolean hasMethod(String name, Object... arguments) {
        applyRulesIfUnknownProperty(name);
        return tasks.hasMethod(name, arguments);
    }

    public Object invokeMethod(String name, Object... arguments) throws MissingMethodException {
        applyRulesIfUnknownProperty(name);
        return tasks.invokeMethod(name, arguments);
    }

    @Override
    public void setProperty(String name, Object value) throws MissingPropertyException {
        throw new UnsupportedOperationException("Set property is not supported!");
    }

    protected String getDisplayName() {
        return null;
    }

    public void setTasks(ConfigurableObjectCollection<Task> tasks) {
        this.tasks = tasks;
    }
}
