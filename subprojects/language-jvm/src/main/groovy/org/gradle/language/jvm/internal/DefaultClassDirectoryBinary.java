/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.jvm.internal;

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.AbstractBuildableModelElement;
import org.gradle.language.base.internal.BinaryInternal;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.language.jvm.ClassDirectoryBinary;

import java.io.File;

public class DefaultClassDirectoryBinary extends AbstractBuildableModelElement implements ClassDirectoryBinary, BinaryInternal {
    private final BinaryNamingScheme namingScheme;
    private final DomainObjectCollection<LanguageSourceSet> source = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);
    private final String name;
    private File classesDir;
    private File resourcesDir;

    public DefaultClassDirectoryBinary(String name) {
        this.name = name;
        this.namingScheme = new ClassDirectoryBinaryNamingScheme(removeClassesSuffix(name));
    }

    private String removeClassesSuffix(String name) {
        if (name.endsWith("Classes")) {
            return name.substring(0, name.length() - 7);
        }
        return name;
    }

    public BinaryNamingScheme getNamingScheme() {
        return namingScheme;
    }

    public String getName() {
        return name;
    }

    public File getClassesDir() {
        return classesDir;
    }

    public void setClassesDir(File classesDir) {
        this.classesDir = classesDir;
    }

    public File getResourcesDir() {
        return resourcesDir;
    }

    public void setResourcesDir(File resourcesDir) {
        this.resourcesDir = resourcesDir;
    }

    public DomainObjectCollection<LanguageSourceSet> getSource() {
        return source;
    }

    public String getDisplayName() {
        return namingScheme.getDescription();
    }

    public String toString() {
        return namingScheme.getDescription();
    }

}
