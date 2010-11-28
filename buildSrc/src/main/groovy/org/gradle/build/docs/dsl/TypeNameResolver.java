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

import org.apache.commons.lang.StringUtils;
import org.gradle.build.docs.dsl.model.ClassMetaData;
import org.gradle.build.docs.model.ClassMetaDataRepository;

import java.util.HashSet;
import java.util.Set;

/**
 * Resolves partial type names into fully qualified type names.
 */
public class TypeNameResolver {
    private final Set<String> primitiveTypes = new HashSet<String>();
    private final ClassMetaDataRepository<ClassMetaData> metaDataRepository;

    public TypeNameResolver(ClassMetaDataRepository<ClassMetaData> metaDataRepository) {
        this.metaDataRepository = metaDataRepository;
        primitiveTypes.add("boolean");
        primitiveTypes.add("byte");
        primitiveTypes.add("char");
        primitiveTypes.add("short");
        primitiveTypes.add("int");
        primitiveTypes.add("long");
        primitiveTypes.add("float");
        primitiveTypes.add("double");
    }

    /**
     * Resolves a source type name into a fully qualified type name. Returns null if the type does not refer to a class
     * to be documented.
     */
    public String resolve(String name, ClassMetaData classMetaData) {
        if (primitiveTypes.contains(name)) {
            return name;
        }

        String candidateClassName;
        String[] innerNames = name.split("\\.");
        ClassMetaData pos = classMetaData;
        for (int i = 0; i < innerNames.length; i++) {
            String innerName = innerNames[i];
            candidateClassName = pos.getClassName() + '.' + innerName;
            if (!pos.getInnerClassNames().contains(candidateClassName)) {
                break;
            }
            if (i == innerNames.length - 1) {
                return candidateClassName;
            }
            pos = metaDataRepository.get(candidateClassName);
        }

        String outerClassName = classMetaData.getOuterClassName();
        while (outerClassName != null) {
            if (name.equals(StringUtils.substringAfterLast(outerClassName, "."))) {
                return outerClassName;
            }
            ClassMetaData outerClass = metaDataRepository.get(outerClassName);
            candidateClassName = outerClassName + '.' + name;
            if (outerClass.getInnerClassNames().contains(candidateClassName)) {
                return candidateClassName;
            }
            outerClassName = outerClass.getOuterClassName();
        }

        if (name.contains(".")) {
            return name;
        }

        for (String importedClass : classMetaData.getImports()) {
            String baseName = StringUtils.substringAfterLast(importedClass, ".");
            if (baseName.equals("*")) {
                candidateClassName = StringUtils.substringBeforeLast(importedClass, ".") + "." + name;
                if (metaDataRepository.find(candidateClassName) != null) {
                    return candidateClassName;
                }
            } else if (name.equals(baseName)) {
                return importedClass;
            }
        }

        candidateClassName = classMetaData.getPackageName() + "." + name;
        if (metaDataRepository.find(candidateClassName) != null) {
            return candidateClassName;
        }

        try {
            candidateClassName = "java.lang." + name;
            ClassLoader.getSystemClassLoader().loadClass(candidateClassName);
            return candidateClassName;
        } catch (ClassNotFoundException e) {
            // Ignore
        }
        
        return name;
    }
}
