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
package org.gradle.plugins.ide.eclipse.model


class WbProperty {
    String name
    String value

    def WbProperty(node) {
        this(node.@name, node.@value)
    }

    def WbProperty(String name, String value) {
        assert name != null && value != null
        this.name = name
        this.value = value
    }

    void appendNode(Node node) {
        node.appendNode("property", [name: name, value: value])
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        WbProperty that = (WbProperty) o;

        if (name != that.name) { return false }
        if (value != that.value) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = name.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    public String toString() {
        return "WbProperty{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}