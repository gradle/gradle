/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.util.GUtil;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public abstract class ConventionTask extends DefaultTask implements IConventionAware {
    private ConventionAwareHelper conventionAwareHelper;
    
    public ConventionTask(Project project, String name) {
        super(project, name);
        conventionAwareHelper = new ConventionAwareHelper(this);
        conventionAwareHelper.setConvention(project.getConvention());
    }

    public Task conventionMapping(Map<String, ConventionValue> mapping) {
        return (Task) conventionAwareHelper.conventionMapping(mapping);
    }

    public Task conventionMapping(String property, ConventionValue mapping) {
        return (Task) conventionAwareHelper.conventionMapping(GUtil.map(property, mapping));
    }

    public Object conventionProperty(String name) {
        return conventionAwareHelper.getConventionValue(name);
    }

    public void setConventionMapping(Map<String, ConventionValue> conventionMapping) {
        conventionAwareHelper.setConventionMapping(conventionMapping);
    }

    public Map<String, ConventionValue> getConventionMapping() {
        return conventionAwareHelper.getConventionMapping();
    }

    public ConventionAwareHelper getConventionAwareHelper() {
        return conventionAwareHelper;
    }

    public void setConventionAwareHelper(ConventionAwareHelper conventionAwareHelper) {
        this.conventionAwareHelper = conventionAwareHelper;
    }

    public Object conv(Object internalValue, String propertyName) {
        return conventionAwareHelper.getConventionValue(internalValue, propertyName);
    }
}
