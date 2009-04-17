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

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Rule;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractDynamicObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultTaskEngine extends AbstractDynamicObject implements TaskEngine {
    private List<Rule> rules = new ArrayList<Rule>();
    private DefaultTaskContainer tasks = new DefaultTaskContainer();

    public Task findTask(String name) {
        if (hasProperty(name)) {
            return getProperty(name);
        }
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
        tasks.add(task.getName(), task);
    }

    public DefaultTaskContainer getTasks() {
        return tasks;
    }

    @Override
    public boolean hasProperty(String name) {
        applyRulesIfUnknownProperty(name);
        return tasks.getAsDynamicObject().hasProperty(name);
    }

    private void applyRulesIfUnknownProperty(String name) {
        if (!tasks.getAsDynamicObject().hasProperty(name)) {
            applyRules(name);
        }
    }

    private void applyRules(String name) {
        for (Rule rule : rules) {
            rule.apply(name);
        }
    }

    @Override
    public Task getProperty(String name) throws MissingPropertyException {
        applyRulesIfUnknownProperty(name);
        return (Task) tasks.getAsDynamicObject().getProperty(name);
    }

    @Override
    public Map<String, Task> getProperties() {
        return tasks.getAsMap();
    }

    @Override
    public boolean hasMethod(String name, Object... arguments) {
        applyRulesIfUnknownProperty(name);
        return tasks.getAsDynamicObject().hasMethod(name, arguments);
    }

    @Override
    public Object invokeMethod(String name, Object... arguments) throws MissingMethodException {
        applyRulesIfUnknownProperty(name);
        return tasks.getAsDynamicObject().invokeMethod(name, arguments);
    }

    protected String getDisplayName() {
        return tasks.getDisplayName();
    }
}
