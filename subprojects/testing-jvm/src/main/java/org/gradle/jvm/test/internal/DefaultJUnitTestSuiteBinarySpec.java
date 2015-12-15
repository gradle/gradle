/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.test.internal;

import com.google.common.collect.Lists;
import org.gradle.jvm.JvmBinaryTasks;
import org.gradle.jvm.internal.*;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.Variant;
import org.gradle.platform.base.binary.BaseBinarySpec;

import java.io.File;
import java.util.Collection;

import static org.gradle.util.CollectionUtils.findSingle;

public class DefaultJUnitTestSuiteBinarySpec extends BaseBinarySpec implements JUnitTestSuiteBinarySpecInternal, WithJvmAssembly, WithDependencies {
    private final JvmBinaryTasks tasks = new DefaultJvmBinaryTasks(super.getTasks());
    private String junitVersion;
    private Collection<DependencySpec> componentLevelDependencies = Lists.newLinkedList();
    private final DefaultJvmAssembly assembly = new DefaultJvmAssembly();

    @Override
    public JUnitTestSuiteSpec getTestSuite() {
        return getComponentAs(JUnitTestSuiteSpec.class);
    }

    @Override
    protected String getTypeName() {
        return "Test";
    }

    @Override
    public JvmBinaryTasks getTasks() {
        return tasks;
    }

    @Override
    @Variant
    public String getJUnitVersion() {
        return junitVersion;
    }

    @Override
    public void setJUnitVersion(String version) {
        this.junitVersion = version;
    }

    @Override
    public void setDependencies(Collection<DependencySpec> dependencies) {
        this.componentLevelDependencies = dependencies;
    }

    @Override
    public Collection<DependencySpec> getDependencies() {
        return componentLevelDependencies;
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
    public JavaToolChain getToolChain() {
        return assembly.getToolChain();
    }

    @Override
    public void setToolChain(JavaToolChain toolChain) {
        assembly.setToolChain(toolChain);
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
    public JvmAssembly getAssembly() {
        return assembly;
    }
}
