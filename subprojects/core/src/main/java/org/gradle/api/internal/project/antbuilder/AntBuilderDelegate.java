/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.project.antbuilder;

import com.google.common.collect.ImmutableSet;
import groovy.util.BuilderSupport;
import groovy.util.Node;
import groovy.util.NodeList;
import groovy.xml.XmlParser;
import org.gradle.internal.Cast;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.DynamicObjectUtil;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class AntBuilderDelegate extends BuilderSupport {

    private final Object originalBuilder;
    private final DynamicObject builder;
    private final ClassLoader antlibClassLoader;

    public AntBuilderDelegate(Object builder, ClassLoader antlibClassLoader) {
        this.originalBuilder = builder;
        this.builder = DynamicObjectUtil.asDynamicObject(builder);
        this.antlibClassLoader = antlibClassLoader;
    }

    public AntBuilderDelegate getAnt() {
        return this;
    }

    public void taskdef(Map<String, String> args) {
        Set<String> argNames = args.keySet();
        if (argNames.equals(ImmutableSet.of("name", "classname"))) {
            try {
                String name = args.get("name");
                String className = args.get("classname");
                addTaskDefinition(name, className);
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        } else if (argNames.equals(Collections.singleton("resource"))) {
            InputStream instr = antlibClassLoader.getResourceAsStream(args.get("resource"));
            try {
                Node xml = new XmlParser().parse(instr);
                for (Object taskdefObject : (NodeList) xml.get("taskdef")) {
                    Node taskdef = (Node) taskdefObject;
                    String name = (String) taskdef.get("@name");
                    String className = (String) taskdef.get("@classname");
                    addTaskDefinition(name, className);
                }
            } catch (Exception ex) {
                throw UncheckedException.throwAsUncheckedException(ex);
            } finally {
                IoActions.closeQuietly(instr);
            }
        } else {
            throw new RuntimeException("Unsupported parameters for taskdef(): " + args);
        }
    }

    private void addTaskDefinition(String name, String className) throws ClassNotFoundException {
        DynamicObject project = DynamicObjectUtil.asDynamicObject(builder.getProperty("project"));
        project.invokeMethod("addTaskDefinition", name, antlibClassLoader.loadClass(className));
    }

    public Object propertyMissing(String name) {
        return builder.getProperty(name);
    }

    @Override
    protected Object createNode(Object name) {
        return builder.invokeMethod("createNode", name);
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        if (name.equals("taskdef")) {
            taskdef(Cast.<Map<String, String>>uncheckedCast(attributes));
        } else {
            return builder.invokeMethod("createNode", name, attributes);
        }
        return null;
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        return builder.invokeMethod("createNode", name, attributes, value);
    }

    @Override
    protected Object createNode(Object name, Object value) {
        return builder.invokeMethod("createNode", name, value);
    }

    @Override
    protected void setParent(Object parent, Object child) {
        builder.invokeMethod("setParent", parent, child);
    }

    @Override
    protected void nodeCompleted(Object parent, Object node) {
        if (parent == null && node == null) {// happens when dispatching to taskdef via createNode()
            return;
        }
        builder.invokeMethod("nodeCompleted", parent, node);
    }

    @Override
    protected Object postNodeCompletion(Object parent, Object node) {
        return builder.invokeMethod("postNodeCompletion", parent, node);
    }

    public Object getBuilder() {
        return originalBuilder;
    }
}
