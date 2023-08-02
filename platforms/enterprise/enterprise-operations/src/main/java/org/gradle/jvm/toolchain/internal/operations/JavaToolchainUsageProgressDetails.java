/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain.internal.operations;


/**
 * Details about the Java tool being used and the toolchain it belongs to.
 *
 * @since 7.6
 */
public interface JavaToolchainUsageProgressDetails {

    /**
     * Name of the tool from Java distribution such as {@code javac}, {@code java} or {@code javadoc}.
     */
    String getToolName();

    /**
     * Toolchain to which the tool belongs.
     */
    JavaToolchain getToolchain();

    @SuppressWarnings("unused")
    interface JavaToolchain {

        /**
         * Returns Java language version such as {@code 11.0.15}.
         */
        String getJavaVersion();

        /**
         * Returns the display name of the toolchain vendor such as {@code Eclipse Temurin}.
         * <p>
         * The value could be normalized for uniformity, and does not necessarily correspond to a system property value.
         */
        String getJavaVendor();

        /**
         * Returns Java runtime name such as {@code OpenJDK Runtime Environment}.
         */
        String getRuntimeName();

        /**
         * Returns Java runtime version such as {@code 17.0.3.1+2-LTS}.
         */
        String getRuntimeVersion();

        /**
         * Returns Java VM name such as {@code OpenJDK 64-Bit Server VM}.
         */
        String getJvmName();

        /**
         * Returns Java VM version such as {@code 17.0.3.1+2-LTS}.
         * <p>
         * This value is identical to {@link #getRuntimeVersion()} for most of the vendors,
         * but could still differ for example by not including the language version.
         */
        String getJvmVersion();

        /**
         * Returns Java VM vendor such as {@code Eclipse Adoptium}.
         */
        String getJvmVendor();

        /**
         * Returns OS architecture such as {@code amd64}.
         */
        String getArchitecture();

    }

}
