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

import org.gradle.api.BuildableComponentSpec;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;

import java.io.File;
import java.util.Set;

/**
 * The smallest unit of deployment of a JVM component:
 *  - a set of directories containing class files
 *  - a set of directories containing resource files
 */
public interface JvmAssembly extends BuildableComponentSpec {
    /**
     * Set of directories containing the class files that
     * belong to this assembly.
     *
     * Modeled as a Set<File> for future proofing but the
     * first implementation can assume a single class directory.
     */
    Set<File> getClassDirectories();

    /**
     * Set of directories containing the resource files that
     * belong to this assembly.
     */
    Set<File> getResourceDirectories();

    /**
     * Returns the {@link org.gradle.jvm.toolchain.JavaToolChain} that will be used to build this assembly.
     */
    JavaToolChain getToolChain();

    /**
     * The target platform for this assembly.
     */
    JavaPlatform getTargetPlatform();
}
