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

package org.gradle.jvm.internal;

import org.gradle.jvm.JvmBinaryTasks;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;
import org.gradle.platform.base.internal.toolchain.ToolResolver;

import java.io.File;

public class DefaultJarBinarySpec extends BaseBinarySpec implements JarBinarySpecInternal {
    private final JvmBinaryTasks tasks = new DefaultJvmBinaryTasks(super.getTasks());
    private JavaToolChain toolChain;
    private JavaPlatform platform;
    private File classesDir;
    private File resourcesDir;
    private File jarFile;
    private String baseName;
    private ToolResolver toolResolver;

    @Override
    protected String getTypeName() {
        return "Jar";
    }

    public String getBaseName() {
        return baseName == null ? getName() : baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public JvmBinaryTasks getTasks() {
        return tasks;
    }

    public JavaToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(JavaToolChain toolChain) {
        this.toolChain = toolChain;
    }

    public ToolResolver getToolResolver() {
        return toolResolver;
    }

    public void setToolResolver(ToolResolver toolResolver) {
        this.toolResolver = toolResolver;
    }

    public JavaPlatform getTargetPlatform() {
        return platform;
    }

    public void setTargetPlatform(JavaPlatform platform) {
        this.platform = platform;
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

    @Override
    protected BinaryBuildAbility getBinaryBuildAbility() {
        return new ToolSearchBuildAbility(getToolResolver().checkToolAvailability(getTargetPlatform()));
    }
}
