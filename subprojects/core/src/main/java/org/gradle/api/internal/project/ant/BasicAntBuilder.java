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
package org.gradle.api.internal.project.ant;

import groovy.util.AntBuilder;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.ant.AntFileResource;
import org.gradle.api.internal.file.ant.BaseDirSelector;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class BasicAntBuilder extends org.gradle.api.AntBuilder implements Closeable {
    private final Field nodeField;
    private final List children;

    public BasicAntBuilder() {
        // These are used to discard references to tasks so they can be garbage collected
        Field collectorField;
        try {
            nodeField = AntBuilder.class.getDeclaredField("lastCompletedNode");
            nodeField.setAccessible(true);
            collectorField = AntBuilder.class.getDeclaredField("collectorTarget");
            collectorField.setAccessible(true);
            Target target = (Target) collectorField.get(this);
            Field childrenField = Target.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            children = (List) childrenField.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        getAntProject().addDataTypeDefinition("gradleFileResource", AntFileResource.class);
        getAntProject().addDataTypeDefinition("gradleBaseDirSelector", BaseDirSelector.class);
    }

    @Override
    public Map<String, Object> getProperties() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getReferences() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importBuild(Object antBuildFile) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importBuild(Object antBuildFile, Transformer<? extends String, ? super String> taskNamer) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void nodeCompleted(Object parent, Object node) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Project.class.getClassLoader());
        try {
            super.nodeCompleted(parent, node);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public void setLifecycleLogLevel(AntMessagePriority logLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AntMessagePriority getLifecycleLogLevel() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object postNodeCompletion(Object parent, Object node) {
        try {
            return nodeField.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object doInvokeMethod(String methodName, Object name, Object args) {
        Object value = super.doInvokeMethod(methodName, name, args);
        // Discard the node so it can be garbage collected. Some Ant tasks cache a potentially large amount of state
        // in fields.
        try {
            nodeField.set(this, null);
            children.clear();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    @Override
    public void close() {
        Project project = getProject();
        project.fireBuildFinished(null);
        ComponentHelper helper = ComponentHelper.getComponentHelper(project);
        helper.getAntTypeTable().clear();
        helper.getDataTypeDefinitions().clear();
        project.getReferences().clear();
    }

}
