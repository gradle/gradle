/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.project.antbuilder

class AntBuilderDelegate extends BuilderSupport {
    final Object builder
    final ClassLoader antlibClassLoader

    def AntBuilderDelegate(builder, antlibClassLoader) {
        this.builder = builder;
        this.antlibClassLoader = antlibClassLoader
    }

    def getAnt() {
        return this
    }

    def taskdef(Map<String, ?> args) {
        if (args.keySet() == ['name', 'classname'] as Set) {
            builder.project.addTaskDefinition(args.name, antlibClassLoader.loadClass(args.classname))
        } else if (args.keySet() == ['resource'] as Set) {
            antlibClassLoader.getResource(args.resource).withInputStream { instr ->
                def xml = new XmlParser().parse(instr)
                xml.taskdef.each {
                    builder.project.addTaskDefinition(it.@name, antlibClassLoader.loadClass(it.@classname))
                }
            }
        } else {
            throw new RuntimeException("Unsupported parameters for taskdef().")
        }
    }

    def propertyMissing(String name) {
        builder."$name"
    }

    protected Object createNode(Object name) {
        builder.createNode(name)
    }

    protected Object createNode(Object name, Map attributes) {
        if (name == "taskdef") {
            taskdef(attributes)
        } else {
            builder.createNode(name, attributes)
        }
    }

    protected Object createNode(Object name, Map attributes, Object value) {
        builder.createNode(name, attributes, value)
    }

    protected Object createNode(Object name, Object value) {
        builder.createNode(name, value)
    }

    protected void setParent(Object parent, Object child) {
        builder.setParent(parent, child)
    }

    protected void nodeCompleted(Object parent, Object node) {
        if (parent == null && node == null) { // happens when dispatching to taskdef via createNode()
            return
        }

        builder.nodeCompleted(parent, node)
    }

    protected Object postNodeCompletion(Object parent, Object node) {
        builder.postNodeCompletion(parent, node)
    }
}
