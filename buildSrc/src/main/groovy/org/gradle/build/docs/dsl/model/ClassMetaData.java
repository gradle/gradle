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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.build.docs.model.Attachable;
import org.gradle.build.docs.model.ClassMetaDataRepository;
import org.gradle.util.GUtil;

import java.io.Serializable;
import java.util.*;

public class ClassMetaData implements Serializable, Attachable<ClassMetaData>, LanguageElement, TypeContainer {
    private final String className;
    private String superClassName;
    private final String packageName;
    private final boolean isInterface;
    private final boolean isGroovy;
    private final String rawCommentText;
    private final List<String> imports = new ArrayList<String>();
    private final List<String> interfaceNames = new ArrayList<String>();
    private final Map<String, PropertyMetaData> declaredProperties = new HashMap<String, PropertyMetaData>();
    private final Set<MethodMetaData> declaredMethods = new HashSet<MethodMetaData>();
    private final List<String> innerClassNames = new ArrayList<String>();
    private String outerClassName;
    private transient ClassMetaDataRepository<ClassMetaData> metaDataRepository;

    public ClassMetaData(String className, String packageName, boolean isInterface, boolean isGroovy, String rawClassComment) {
        this.className = className;
        this.packageName = packageName;
        this.isInterface = isInterface;
        this.isGroovy = isGroovy;
        this.rawCommentText = rawClassComment;
    }

    public ClassMetaData(String className) {
        this(className, StringUtils.substringBeforeLast(className, "."), false, false, "");
    }

    @Override
    public String toString() {
        return className;
    }

    public String getClassName() {
        return className;
    }

    public String getSimpleName() {
        return StringUtils.substringAfterLast(className, ".");
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public boolean isGroovy() {
        return isGroovy;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public void setSuperClassName(String superClassName) {
        this.superClassName = superClassName;
    }

    public ClassMetaData getSuperClass() {
        return superClassName == null ? null : metaDataRepository.find(superClassName);
    }

    public List<String> getInterfaceNames() {
        return interfaceNames;
    }

    public void addInterfaceName(String name) {
        interfaceNames.add(name);
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

    public List<String> getInnerClassNames() {
        return innerClassNames;
    }

    public void addInnerClassName(String innerClassName) {
        innerClassNames.add(innerClassName);
    }

    public String getOuterClassName() {
        return outerClassName;
    }

    public void setOuterClassName(String outerClassName) {
        this.outerClassName = outerClassName;
    }

    public String getRawCommentText() {
        return rawCommentText;
    }

    public List<String> getImports() {
        return imports;
    }

    public void addImport(String importName) {
        imports.add(importName);
    }

    public PropertyMetaData addReadableProperty(String name, TypeMetaData type, String rawCommentText) {
        PropertyMetaData property = getProperty(name);
        property.setType(type);
        property.setRawCommentText(rawCommentText);
        return property;
    }

    public PropertyMetaData addWriteableProperty(String name, TypeMetaData type, String rawCommentText) {
        PropertyMetaData property = getProperty(name);
        if (property.getType() == null) {
            property.setType(type);
        }
        if (!GUtil.isTrue(property.getRawCommentText())) {
            property.setRawCommentText(rawCommentText);
        }
        property.setWriteable(true);
        return property;
    }

    public PropertyMetaData findDeclaredProperty(String name) {
        return declaredProperties.get(name);
    }

    public Set<String> getDeclaredPropertyNames() {
        return declaredProperties.keySet();
    }

    public Set<PropertyMetaData> getDeclaredProperties() {
        return new HashSet<PropertyMetaData>(declaredProperties.values());
    }

    public Set<MethodMetaData> getDeclaredMethods() {
        return declaredMethods;
    }

    /**
     * Finds a property by name. Includes inherited properties.
     *
     * @param name The property name.
     * @return The property, or null if no such property exists.
     */
    public PropertyMetaData findProperty(String name) {
        PropertyMetaData propertyMetaData = declaredProperties.get(name);
        if (propertyMetaData != null) {
            return propertyMetaData;
        }
        ClassMetaData superClass = getSuperClass();
        if (superClass != null) {
            return superClass.findProperty(name);
        }
        return null;
    }

    /**
     * Returns the set of property names for this class, including inherited properties.
     *
     * @return The set of property names.
     */
    public Set<String> getPropertyNames() {
        Set<String> propertyNames = new TreeSet<String>();
        propertyNames.addAll(declaredProperties.keySet());
        ClassMetaData superClass = getSuperClass();
        if (superClass != null) {
            propertyNames.addAll(superClass.getPropertyNames());
        }
        return propertyNames;
    }

    private PropertyMetaData getProperty(String name) {
        PropertyMetaData property = declaredProperties.get(name);
        if (property == null) {
            property = new PropertyMetaData(name, this);
            declaredProperties.put(name, property);
        }
        return property;
    }

    public void attach(ClassMetaDataRepository<ClassMetaData> metaDataRepository) {
        this.metaDataRepository = metaDataRepository;
    }

    public MethodMetaData addMethod(String name, TypeMetaData returnType, String rawCommentText) {
        MethodMetaData method = new MethodMetaData(name, this);
        declaredMethods.add(method);
        method.setReturnType(returnType);
        method.setRawCommentText(rawCommentText);
        return method;
    }

    public void visitTypes(Action<TypeMetaData> action) {
        for (PropertyMetaData propertyMetaData : declaredProperties.values()) {
            propertyMetaData.visitTypes(action);
        }
        for (MethodMetaData methodMetaData : declaredMethods) {
            methodMetaData.visitTypes(action);
        }
    }
}
