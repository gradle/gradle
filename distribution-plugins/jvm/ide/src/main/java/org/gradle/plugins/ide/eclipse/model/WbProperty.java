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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import groovy.util.Node;

import java.util.Map;

/**
 * A wtp descriptor property entry.
 */
public class WbProperty implements WbModuleEntry {
    private String name;
    private String value;

    public WbProperty(Node node) {
        this((String) node.attribute("name"), (String) node.attribute("value"));
    }

    public WbProperty(String name, String value) {
        this.name = Preconditions.checkNotNull(name);
        this.value = Preconditions.checkNotNull(value);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public void appendNode(Node node) {
        Map<String, Object> attributes = Maps.newLinkedHashMap();
        attributes.put("name", name);
        attributes.put("value", value);
        node.appendNode("property", attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WbProperty that = (WbProperty) o;
        return Objects.equal(name, that.name) && Objects.equal(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, value);
    }

    @Override
    public String toString() {
        return "WbProperty{name='" + name + "', value='" + value + "'}";
    }
}
