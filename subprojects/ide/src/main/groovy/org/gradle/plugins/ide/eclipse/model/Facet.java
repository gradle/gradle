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
 * A project facet.
 */
public class Facet {

    /**
     * An {@code installed} facet is really present on an Eclipse project whereas facet type {@code fixed} means that
     * this facet is locked and cannot be simply removed. See also
     * <a href="https://eclipse.org/articles/Article-BuildingProjectFacets/tutorial.html#defining.presets">here</a>.
     */
    @SuppressWarnings("FieldName")
    public enum FacetType { installed, fixed }

    private FacetType type;
    private String name;
    private String version;

    public Facet() {
        type = FacetType.installed;
    }

    public Facet(Node node) {
        this(FacetType.valueOf((String) node.name()), (String) node.attribute("facet"), (String) node.attribute("version"));
    }

    public Facet(String name, String version) {
        this(FacetType.installed, name, version);
    }

    public Facet(FacetType type, String name, String version) {
        Preconditions.checkNotNull(type);
        Preconditions.checkNotNull(name);
        if (type == FacetType.installed) {
            Preconditions.checkNotNull(version);
        } else {
            Preconditions.checkArgument(version == null);
        }
        this.type = type;
        this.name = name;
        this.version = version;
    }

    public FacetType getType() {
        return type;
    }

    public void setType(FacetType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void appendNode(Node node) {
        Map<String, Object> attributes = Maps.newHashMap();
        attributes.put("facet", name);
        if (type == FacetType.installed) {
            attributes.put("version", version);
        }
        node.appendNode(type.name(), attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Facet facet = (Facet) o;
        return type == facet.type && Objects.equal(name, facet.name) && Objects.equal(version, facet.version);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, name, version);
    }

    @Override
    public String toString() {
        return "Facet{type='" + type + "', name='" + name + "', version='" + version + "'}";
    }
}
