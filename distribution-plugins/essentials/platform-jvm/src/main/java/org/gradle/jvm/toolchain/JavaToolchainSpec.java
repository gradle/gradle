/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain;

import org.gradle.api.Describable;
import org.gradle.api.provider.Property;
import org.gradle.internal.HasInternalProtocol;

/**
 * Requirements for selecting a Java toolchain.
 * <p>
 * A toolchain is a JRE/JDK used by the tasks of a build. Tasks of a build may require one or more of the tools javac, java, or javadoc) of a toolchain.
 * Depending on the needs of a build, only toolchains matching specific characteristics can be used to run a build or a specific task of a build.
 *
 * @since 6.7
 */
@HasInternalProtocol
public interface JavaToolchainSpec extends Describable {

    /**
     * The exact version of the Java language that the toolchain is required to support.
     */
    Property<JavaLanguageVersion> getLanguageVersion();

    /**
     * The vendor of the toolchain.
     * <p>By default, toolchains from any vendor are eligible.</p>
     *
     * @since 6.8
     */
    Property<JvmVendorSpec> getVendor();

    /**
     * The virtual machine implementation of the toolchain.
     * <p>By default, any implementation (hotspot, j9, ...) is eligible.</p>
     *
     * @since 6.8
     */
    Property<JvmImplementation> getImplementation();

}
