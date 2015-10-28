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

package org.gradle.api.plugins.quality.internal.forking

import org.gradle.api.internal.project.AntBuilderDelegate

class AntSourceBuilder extends BuilderSupport implements Serializable {

    private final String name;
    private final Map properties;
    private final List<AntSourceBuilder> children = new LinkedList<>();

    AntSourceBuilder(String name, Map properties) {
        this.name = name;
        this.properties = properties;
    }

    public String getName() {
        return name;
    }

    public Map getSourceProperties() {
        return properties.findAll { key, value -> value instanceof String }
    }

    public Map<String, String> getSourcePropertiesKey() {
        return properties.collectEntries { key, value -> [key, value.class.getName()]}
    }

    public List<AntSourceBuilder> getChildren() {
        return children;
    }

    @Override
    protected void setParent(Object parent, Object child) {
        ((AntSourceBuilder)parent).children.add(child)
        children.remove(child)
    }

    @Override
    protected Object createNode(Object name) {
        return createNode(name, null, null)
    }

    @Override
    protected Object createNode(Object name, Object value) {
        return createNode(name, null, null)
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        return createNode(name, attributes, null)
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        return createNewSourceBuilder(name.toString(), attributes)
    }

    private AntSourceBuilder createNewSourceBuilder(String name, args) {
        def builder = new AntSourceBuilder(name, args == null ? new HashMap<>() : (Map) args)
        children.add(builder)
        return builder
    }

    public void apply(AntBuilderDelegate antBuilder) {
        antBuilder."$name"(properties) {
            children.each { it.apply(antBuilder) }
        }
    }
}
