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

package org.gradle.jvm.internal;

import org.gradle.api.internal.AbstractBuildableComponentSpec;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultJvmAssembly extends AbstractBuildableComponentSpec implements JvmAssembly {

    private LinkedHashSet<File> classDirectories = new LinkedHashSet<File>();
    private LinkedHashSet<File> resourceDirectories = new LinkedHashSet<File>();
    private JavaToolChain toolChain;
    private JavaPlatform targetPlatform;

    public DefaultJvmAssembly(ComponentSpecIdentifier identifier) {
        super(identifier, JvmAssembly.class);
    }

    protected DefaultJvmAssembly(ComponentSpecIdentifier identifier, Class<? extends JvmAssembly> publicType) {
        super(identifier, publicType);
    }

    @Override
    public Set<File> getClassDirectories() {
        return classDirectories;
    }

    @Override
    public Set<File> getResourceDirectories() {
        return resourceDirectories;
    }

    @Override
    public JavaToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(JavaToolChain toolChain) {
        this.toolChain = toolChain;
    }

    @Override
    public JavaPlatform getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(JavaPlatform targetPlatform) {
        this.targetPlatform = targetPlatform;
    }
}
