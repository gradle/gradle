/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.runtime.jvm.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetContainer;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.jvm.JvmBinaryTasks;
import org.gradle.runtime.jvm.JvmLibrarySpec;
import org.gradle.runtime.jvm.toolchain.JavaToolChain;

import java.io.File;

public class DefaultJarBinarySpec extends AbstractBuildableModelElement implements JarBinarySpecInternal {
    private final LanguageSourceSetContainer sourceSets = new LanguageSourceSetContainer();
    private final JvmLibrarySpec library;
    private final BinaryNamingScheme namingScheme;
    private final JavaToolChain toolChain;
    private final DefaultJvmBinaryTasks tasks = new DefaultJvmBinaryTasks(this);
    private File classesDir;
    private File resourcesDir;
    private File jarFile;

    public DefaultJarBinarySpec(JvmLibrarySpec library, BinaryNamingScheme namingScheme, JavaToolChain toolChain) {
        this.library = library;
        this.namingScheme = namingScheme;
        this.toolChain = toolChain;
    }

    public boolean isBuildable() {
        return true;
    }

    public boolean isLegacyBinary() {
        return false;
    }

    public String getDisplayName() {
        return namingScheme.getDescription();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getName() {
        return namingScheme.getLifecycleTaskName();
    }

    public JvmLibrarySpec getLibrary() {
        return library;
    }

    public JavaToolChain getToolChain() {
        return toolChain;
    }

    public BinaryNamingScheme getNamingScheme() {
        return namingScheme;
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return sourceSets;
    }

    public void source(Object sources) {
        sourceSets.source(sources);
    }

    public JvmBinaryTasks getTasks() {
        return tasks;
    }

    public File getJarFile() {
        return jarFile;
    }

    public void setJarFile(File jarFile) {
        this.jarFile = jarFile;
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
}
