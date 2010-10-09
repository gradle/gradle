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
package org.gradle.build.docs.dsl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ClassMetaData implements Serializable {
    private final String superClassName;
    private final boolean groovy;
    private final Map<String, PropertyMetaData> classProperties = new HashMap<String, PropertyMetaData>();

    public ClassMetaData(String superClassName, boolean isGroovy) {
        this.superClassName = superClassName;
        groovy = isGroovy;
    }

    public Map<String, PropertyMetaData> getClassProperties() {
        return classProperties;
    }

    public boolean isGroovy() {
        return groovy;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public void addReadableProperty(String name, String type) {
        PropertyMetaData property = getProperty(name);
        property.setType(type);
    }

    public void addWriteableProperty(String name, String type) {
        PropertyMetaData property = getProperty(name);
        property.setWriteable(true);
    }

    private PropertyMetaData getProperty(String name) {
        PropertyMetaData property = classProperties.get(name);
        if (property == null) {
            property = new PropertyMetaData();
            classProperties.put(name, property);
        }
        return property;
    }
}
