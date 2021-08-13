/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.Incubating;

import java.util.Optional;

/**
 * Represents a candidate Java toolchain, before it's provisioned.
 * A toolchain provider must minimally supply the language version and
 * vendor.
 *
 * @since 7.3
 */
@Incubating
public interface JavaToolchainCandidate {

    /**
     * Returns the language version of this toolchain
     */
    JavaLanguageVersion getLanguageVersion();

    /**
     * Returns the vendor string
     */
    String getVendor();

    /**
     * Returns the implementation of the JDK
     */
    Optional<JvmImplementation> getImplementation();

    /**
     * Returns the architecture of the JDK. Defaults to the current build architecture.
     */
    String getArch();

    /**
     * Returns the operating system of the JDK. Defaults to the current build OS.
     */
    String getOperatingSystem();

    /**
     * Candidate builder entry point.
     *
     * @since 7.3
     */
    @Incubating
    interface Builder {
        BuilderWithVendor withVendor(String vendor);
    }

    /**
     * Candidate builder
     *
     * @since 7.3
     */
    @Incubating
    interface BuilderWithVendor {
        /**
         * Sets the language version of this candidate
         * @param version the version
         */
        BuilderWithLanguageVersion withLanguageVersion(int version);

        /**
         * Sets the language version of this candidate
         * @param version the version
         */
        BuilderWithLanguageVersion withLanguageVersion(String version);

        /**
         * Sets the language version of this candidate
         * @param version the version
         */
        BuilderWithLanguageVersion withLanguageVersion(JavaLanguageVersion version);
    }

    /**
     * Candidate builder entry point.
     *
     * @since 7.3
     */
    @Incubating
    interface BuilderWithLanguageVersion {
        /**
         * Sets the implementation of this candidate
         * @param implementation the implementation
         */
        BuilderWithLanguageVersion withImplementation(JvmImplementation implementation);

        /**
         * Sets the architecture  of this candidate
         * @param arch the architecture
         */
        BuilderWithLanguageVersion withArch(String arch);

        /**
         * Sets the operating system of this candidate
         * @param os the operating system
         */
        BuilderWithLanguageVersion withOperatingSystem(String os);

        /**
         * Builds the candidate
         */
        JavaToolchainCandidate build();
    }
}
