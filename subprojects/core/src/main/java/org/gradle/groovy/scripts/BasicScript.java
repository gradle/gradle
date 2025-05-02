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

package org.gradle.groovy.scripts;

import groovy.lang.Binding;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.project.DynamicLookupRoutine;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.DynamicObjectUtil;
import org.gradle.internal.scripts.GradleScript;
import org.gradle.internal.service.ServiceRegistry;
import org.jspecify.annotations.Nullable;

import java.io.PrintStream;
import java.util.Map;

public abstract class BasicScript extends org.gradle.groovy.scripts.Script implements org.gradle.api.Script, DynamicObjectAware, GradleScript {
    private StandardOutputCapture standardOutputCapture;
    private Object target;
    private final ScriptDynamicObject dynamicObject = new ScriptDynamicObject(this);
    private DynamicLookupRoutine dynamicLookupRoutine;

    @Override
    public void init(Object target, ServiceRegistry services) {
        standardOutputCapture = services.get(StandardOutputCapture.class);
        dynamicLookupRoutine = services.get(DynamicLookupRoutine.class);
        setScriptTarget(target);
    }

    public Object getScriptTarget() {
        return target;
    }

    private void setScriptTarget(Object target) {
        this.target = target;
        this.dynamicObject.setTarget(target);
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return standardOutputCapture;
    }

    public PrintStream getOut() {
        return System.out;
    }

    @Override
    public Object getProperty(String property) {
        return dynamicLookupRoutine.property(dynamicObject, property);
    }

    @Override
    public void setProperty(String property, Object newValue) {
        dynamicLookupRoutine.setProperty(dynamicObject, property, newValue);
    }

    public Map<String, ? extends @Nullable Object> getProperties() {
        return dynamicLookupRoutine.getProperties(dynamicObject);
    }

    public boolean hasProperty(String property) {
        return dynamicLookupRoutine.hasProperty(dynamicObject, property);
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        return dynamicLookupRoutine.invokeMethod(dynamicObject, name, (Object[]) args);
    }

    @Override
    public DynamicObject getAsDynamicObject() {
        return dynamicObject;
    }

    /**
     * This is a performance optimization which avoids using BeanDynamicObject to wrap the Script object.
     * Using BeanDynamicObject would be wasteful, because most of the interesting properties and methods
     * are delegated to the script target. Doing this delegation explicitly avoids
     * us going through the methodMissing/propertyMissing protocol that BeanDynamicObject would use.
     */
    private static final class ScriptDynamicObject extends AbstractDynamicObject {

        private final Binding binding;
        private final DynamicObject scriptObject;
        private DynamicObject dynamicTarget;

        ScriptDynamicObject(BasicScript script) {
            this.binding = script.getBinding();
            scriptObject = new BeanDynamicObject(script).withNotImplementsMissing();
            dynamicTarget = scriptObject;
        }

        public void setTarget(Object target) {
            dynamicTarget = DynamicObjectUtil.asDynamicObject(target);
        }

        @Override
        public Map<String, ? extends @Nullable Object> getProperties() {
            return dynamicTarget.getProperties();
        }

        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return scriptObject.hasMethod(name, arguments) || dynamicTarget.hasMethod(name, arguments);
        }

        @Override
        public boolean hasProperty(String name) {
            return binding.hasVariable(name) || scriptObject.hasProperty(name) || dynamicTarget.hasProperty(name);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            DynamicInvokeResult result = scriptObject.tryInvokeMethod(name, arguments);
            if (result.isFound()) {
                return result;
            }
            return dynamicTarget.tryInvokeMethod(name, arguments);
        }

        @Override
        public DynamicInvokeResult tryGetProperty(String property) {
            if (binding.hasVariable(property)) {
                return DynamicInvokeResult.found(binding.getVariable(property));
            }
            DynamicInvokeResult result = scriptObject.tryGetProperty(property);
            if (result.isFound()) {
                return result;
            }
            return dynamicTarget.tryGetProperty(property);
        }

        @Override
        public DynamicInvokeResult trySetProperty(String property, Object newValue) {
            return dynamicTarget.trySetProperty(property, newValue);
        }

        @Override
        public DynamicInvokeResult trySetPropertyWithoutInstrumentation(String name, Object value) {
            return dynamicTarget.trySetPropertyWithoutInstrumentation(name, value);
        }

        @Override
        public MissingPropertyException getMissingProperty(String name) {
            return dynamicTarget.getMissingProperty(name);
        }

        @Override
        public MissingMethodException methodMissingException(String name, Object... params) {
            return dynamicTarget.methodMissingException(name, params);
        }

        @Override
        public MissingPropertyException setMissingProperty(String name) {
            return dynamicTarget.setMissingProperty(name);
        }

        @Override
        public String getDisplayName() {
            return dynamicTarget.toString();
        }
    }

}


