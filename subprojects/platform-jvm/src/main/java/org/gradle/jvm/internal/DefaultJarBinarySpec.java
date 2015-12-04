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

import com.google.common.collect.ImmutableSet;
import org.gradle.jvm.JvmBinaryTasks;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import static org.gradle.util.CollectionUtils.findSingle;

public class DefaultJarBinarySpec extends BaseBinarySpec implements JarBinarySpecInternal {
    private final JvmBinaryTasks tasks = new DefaultJvmBinaryTasks(super.getTasks());
    private final JarFile apiJar = new DefaultJarFile();
    private File jarFile;
    private Set<String> exportedPackages = ImmutableSet.of();
    private Set<DependencySpec> apiDependencies = ImmutableSet.of();
    private Set<DependencySpec> componentLevelDependencies = ImmutableSet.of();
    private final DefaultJvmAssembly assembly = new DefaultJvmAssembly();

    @Override
    protected String getTypeName() {
        return "Jar";
    }

    @Override
    public JvmBinaryTasks getTasks() {
        return tasks;
    }

    @Override
    public JavaToolChain getToolChain() {
        return assembly.getToolChain();
    }

    @Override
    public void setToolChain(JavaToolChain toolChain) {
        assembly.setToolChain(toolChain);
    }

    @Override
    public JavaPlatform getTargetPlatform() {
        return assembly.getTargetPlatform();
    }

    @Override
    public void setTargetPlatform(JavaPlatform platform) {
        assembly.setTargetPlatform(platform);
    }

    @Override
    public JarFile getApiJar() {
        return apiJar;
    }

    @Override
    public File getJarFile() {
        return jarFile;
    }

    @Override
    public void setJarFile(File jarFile) {
        this.jarFile = jarFile;
    }

    @Override
    public File getApiJarFile() {
        return apiJar.getFile();
    }

    @Override
    public void setApiJarFile(File apiJarFile) {
        apiJar.setFile(apiJarFile);
    }

    @Override
    public File getClassesDir() {
        return findSingle(assembly.getClassDirectories());
    }

    @Override
    public void setClassesDir(File classesDir) {
        replaceSingleDirectory(assembly.getClassDirectories(), classesDir);
    }

    @Override
    public File getResourcesDir() {
        return findSingle(assembly.getResourceDirectories());
    }

    @Override
    public void setResourcesDir(File resourcesDir) {
        replaceSingleDirectory(assembly.getResourceDirectories(), resourcesDir);
    }

    @Override
    public void setExportedPackages(Set<String> exportedPackages) {
        this.exportedPackages = ImmutableSet.copyOf(exportedPackages);
    }

    @Override
    public Set<String> getExportedPackages() {
        return exportedPackages;
    }

    @Override
    public void setApiDependencies(Collection<DependencySpec> apiDependencies) {
        this.apiDependencies = ImmutableSet.copyOf(apiDependencies);
    }

    @Override
    public Collection<DependencySpec> getApiDependencies() {
        return apiDependencies;
    }

    @Override
    public void setDependencies(Collection<DependencySpec> dependencies) {
        componentLevelDependencies = ImmutableSet.copyOf(dependencies);
    }

    @Override
    public Collection<DependencySpec> getDependencies() {
        return componentLevelDependencies;
    }

    @Override
    protected BinaryBuildAbility getBinaryBuildAbility() {
        return new ToolSearchBuildAbility(((JavaToolChainInternal) getToolChain()).select(getTargetPlatform()));
    }

    @Override
    public JvmAssembly getAssembly() {
        return assembly;
    }

    private void replaceSingleDirectory(Set<File> dirs, File dir) {
        switch (dirs.size()) {
            case 0:
                dirs.add(dir);
                break;
            case 1:
                dirs.clear();
                dirs.add(dir);
                break;
            default:
                throw new IllegalStateException("Can't replace multiple directories.");
        }
    }

}
