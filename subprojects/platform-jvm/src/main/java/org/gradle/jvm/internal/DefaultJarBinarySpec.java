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
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.tasks.Jar;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public class DefaultJarBinarySpec extends DefaultJvmBinarySpec implements JarBinarySpecInternal {
    private final JarFile apiJar;
    private final JarFile jarFile;
    private Set<String> exportedPackages = ImmutableSet.of();
    private Set<DependencySpec> apiDependencies = ImmutableSet.of();
    private Set<DependencySpec> componentLevelDependencies = ImmutableSet.of();
    private final DefaultTasksCollection tasks = new DefaultTasksCollection(super.getTasks());

    public DefaultJarBinarySpec() {
        apiJar = childJarFile("apiJarFile");
        jarFile = childJarFile("jarFile");
    }

    private DefaultJarFile childJarFile(String childName) {
        return new DefaultJarFile(getIdentifier().child(childName));
    }

    @Override
    public TasksCollection getTasks() {
        return tasks;
    }

    @Override
    public JvmLibrarySpec getLibrary() {
        return getComponentAs(JvmLibrarySpec.class);
    }

    @Override
    protected String getTypeName() {
        return "Jar";
    }

    @Override
    public JarFile getApiJar() {
        return apiJar;
    }

    @Override
    public JarFile getRuntimeJar() {
        return jarFile;
    }

    @Override
    public File getJarFile() {
        return jarFile.getFile();
    }

    @Override
    public void setJarFile(File jarFile) {
        this.jarFile.setFile(jarFile);
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

    static class DefaultTasksCollection extends BinaryTasksCollectionWrapper implements TasksCollection {
        public DefaultTasksCollection(BinaryTasksCollection delegate) {
            super(delegate);
        }

        @Override
        public Jar getJar() {
            return findSingleTaskWithType(Jar.class);
        }
    }
}
