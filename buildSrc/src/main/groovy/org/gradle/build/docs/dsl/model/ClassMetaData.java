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
package org.gradle.build.docs.dsl.model;

import java.io.Serializable;
import java.util.*;

import org.gradle.build.docs.model.Attachable;
import org.gradle.build.docs.model.ClassMetaDataRepository;

public class ClassMetaData implements Serializable, Attachable<ClassMetaData> {
    private final String className;
    private final String superClassName;
    private final boolean groovy;
    private final String rawCommentText;
    private final List<String> imports;
    private final List<String> interfaceNames;
    private final Map<String, PropertyMetaData> classProperties = new HashMap<String, PropertyMetaData>();
    private ClassMetaDataRepository<ClassMetaData> metaDataRepository;

    public ClassMetaData(String className, String superClassName, boolean isGroovy, String rawClassComment, List<String> imports, List<String> interfaceNames) {
        this.className = className;
        this.superClassName = superClassName;
        groovy = isGroovy;
        this.rawCommentText = rawClassComment;
        this.imports = imports;
        this.interfaceNames = interfaceNames;
    }

    public ClassMetaData(String className) {
        this(className, null, false, "", Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    @Override
    public String toString() {
        return className;
    }

    public Map<String, PropertyMetaData> getClassProperties() {
        return classProperties;
    }

    public String getClassName() {
        return className;
    }

    public boolean isGroovy() {
        return groovy;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public ClassMetaData getSuperClass() {
        return superClassName == null ? null : metaDataRepository.find(superClassName);
    }

    public List<ClassMetaData> getInterfaces() {
        List<ClassMetaData> interfaces = new ArrayList<ClassMetaData>();
        for (String interfaceName : interfaceNames) {
            ClassMetaData interfaceMetaData = metaDataRepository.find(interfaceName);
            if (interfaceMetaData != null) {
                interfaces.add(interfaceMetaData);
            }
        }
        return interfaces;
    }

    public String getRawCommentText() {
        return rawCommentText;
    }

    public List<String> getImports() {
        return imports;
    }

    public void addReadableProperty(String name, String type, String rawCommentText) {
        PropertyMetaData property = getProperty(name);
        property.setType(type);
        property.setRawCommentText(rawCommentText);
    }

    public void addWriteableProperty(String name, String type, String rawCommentText) {
        PropertyMetaData property = getProperty(name);
        property.setWriteable(true);
    }

    public PropertyMetaData findProperty(String name) {
        return classProperties.get(name);
    }
    
    private PropertyMetaData getProperty(String name) {
        PropertyMetaData property = classProperties.get(name);
        if (property == null) {
            property = new PropertyMetaData(name);
            classProperties.put(name, property);
        }
        return property;
    }

    public void attach(ClassMetaDataRepository<ClassMetaData> metaDataRepository) {
        this.metaDataRepository = metaDataRepository;
        for (PropertyMetaData propertyMetaData : classProperties.values()) {
            propertyMetaData.attach(this);
        }
    }
}
