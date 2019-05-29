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
package org.gradle.build.docs.dsl.source;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.build.docs.dsl.source.model.ClassMetaData;
import org.gradle.build.docs.dsl.source.model.TypeMetaData;
import org.gradle.build.docs.model.ClassMetaDataRepository;
import org.gradle.internal.UncheckedException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves partial type names into fully qualified type names.
 */
public class TypeNameResolver {
    private final Set<String> primitiveTypes = new HashSet<String>();
    private final List<String> groovyImplicitImportPackages = new ArrayList<String>();
    private final List<String> groovyImplicitTypes = new ArrayList<String>();
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
        primitiveTypes.add("void");
        groovyImplicitImportPackages.add("java.util.");
        groovyImplicitImportPackages.add("java.io.");
        groovyImplicitImportPackages.add("java.net.");
        groovyImplicitImportPackages.add("groovy.lang.");
        groovyImplicitImportPackages.add("groovy.util.");
        groovyImplicitTypes.add("java.math.BigDecimal");
        groovyImplicitTypes.add("java.math.BigInteger");

        // check that groovy is visible.
        try {
            getClass().getClassLoader().loadClass("groovy.lang.Closure");
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Resolves the names in the given type into fully qualified names.
     */
    public void resolve(final TypeMetaData type, final ClassMetaData classMetaData) {
        type.visitTypes(new Action<TypeMetaData>() {
            @Override
            public void execute(TypeMetaData t) {
                t.setName(resolve(t.getName(), classMetaData));
            }
        });
    }

    /**
     * Resolves a source type name into a fully qualified type name.
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
                if (importedClass.startsWith("java.") && isVisibleSystemClass(candidateClassName)) {
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

        candidateClassName = "java.lang." + name;
        if (isVisibleSystemClass(candidateClassName)) {
            return candidateClassName;
        }

        if (classMetaData.isGroovy()) {
            candidateClassName = "java.math." + name;
            if (groovyImplicitTypes.contains(candidateClassName)) {
                return candidateClassName;
            }
            for (String prefix : groovyImplicitImportPackages) {
                candidateClassName = prefix + name;
                if (isVisibleSystemClass(candidateClassName)) {
                    return candidateClassName;
                }
            }
        }

        return name;
    }

    // Only use for system Java/Groovy classes; arbitrary use on the build classpath will result in class/jar leaks.
    private boolean isVisibleSystemClass(String candidateClassName) {
        try {
            getClass().getClassLoader().loadClass(candidateClassName);
            return true;
        } catch (ClassNotFoundException e) {
            // Ignore
        }
        return false;
    }
}
