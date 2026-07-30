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
import groovy.util.Node;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A wtp descriptor property entry.
 * @since 1.0
 */
public class WbProperty implements WbModuleEntry {
    private String name;
    private String value;

    /**
     * Creates a new {@code WbProperty}.
     *
     * @since 2.14
     */
    public WbProperty(Node node) {
        this((String) node.attribute("name"), (String) node.attribute("value"));
    }

    /**
     * Creates a new {@code WbProperty}.
     *
     * @since 1.0
     */
    public WbProperty(String name, String value) {
        this.name = Preconditions.checkNotNull(name);
        this.value = Preconditions.checkNotNull(value);
    }

    /**
     * Returns the name.
     *
     * @since 1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @since 1.0
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the value.
     *
     * @since 1.0
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value.
     *
     * @since 1.0
     */
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public void appendNode(Node node) {
        Map<String, Object> attributes = new LinkedHashMap<>();
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
