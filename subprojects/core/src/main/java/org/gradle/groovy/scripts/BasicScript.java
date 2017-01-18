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

import groovy.lang.MetaClass;
import org.gradle.api.internal.DynamicObjectUtil;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.GetPropertyResult;
import org.gradle.internal.metaobject.InvokeMethodResult;
import org.gradle.internal.service.ServiceRegistry;

import java.io.PrintStream;
import java.util.Map;

public abstract class BasicScript extends org.gradle.groovy.scripts.Script implements org.gradle.api.Script, FileOperations, ProcessOperations {
    private StandardOutputCapture standardOutputCapture;
    private Object target;
    private DynamicObject dynamicTarget;
    private final DynamicObject scriptObject = new BeanDynamicObject(this).withNotImplementsMissing();

    public void init(Object target, ServiceRegistry services) {
        standardOutputCapture = services.get(StandardOutputCapture.class);
        setScriptTarget(target);
    }

    public Object getScriptTarget() {
        return target;
    }

    private void setScriptTarget(Object target) {
        this.target = target;
        this.dynamicTarget = DynamicObjectUtil.asDynamicObject(target);
    }

    protected DynamicObject getDynamicTarget() {
        return dynamicTarget;
    }

    public StandardOutputCapture getStandardOutputCapture() {
        return standardOutputCapture;
    }

    public PrintStream getOut() {
        return System.out;
    }

    @Override
    public Object getProperty(String property) {
        // Refactoring of groovy.lang.Script.getProperty logic
        // to avoid unnecessary MissingPropertyException in binding variable lookup
        if (getBinding().hasVariable(property)) {
            return super.getProperty(property);
        }
        GetPropertyResult result = new GetPropertyResult();
        scriptObject.getProperty(property, result);
        if (result.isFound()) {
            return result.getValue();
        }
        getDynamicTarget().getProperty(property, result);
        if (result.isFound()) {
            return result.getValue();
        }
        throw getDynamicTarget().getMissingProperty(property);
    }

    @Override
    public void setProperty(String property, Object newValue) {
        if ("metaClass".equals(property)) {
            setMetaClass((MetaClass) newValue);
        } else if ("scriptTarget".equals(property)) {
            setScriptTarget(newValue);
        } else {
            getDynamicTarget().setProperty(property, newValue);
        }
    }

    public Map<String, ?> getProperties() {
        return getDynamicTarget().getProperties();
    }

    public boolean hasProperty(String property) {
        return getBinding().hasVariable(property) || scriptObject.hasProperty(property) || getDynamicTarget().hasProperty(property);
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        Object[] arguments = (Object[]) args;
        InvokeMethodResult result = new InvokeMethodResult();
        scriptObject.invokeMethod(name, result, arguments);
        if (result.isFound()) {
            return result.getResult();
        }
        getDynamicTarget().invokeMethod(name, result, arguments);
        if (result.isFound()) {
            return result.getResult();
        }
        throw getDynamicTarget().methodMissingException(name, arguments);
    }

    public Object methodMissing(String name, Object args) {
        // In some cases the meta class will invoke this before invokeMethod()
        return invokeMethod(name, args);
    }
}


