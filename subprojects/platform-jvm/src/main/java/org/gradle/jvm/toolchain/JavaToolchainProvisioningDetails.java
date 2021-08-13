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
import org.gradle.internal.HasInternalProtocol;

import java.util.List;

/**
 * Toolchain provisioning details are used by toolchain provisioners
 * to list the candidates they can offer for a particular request.
 *
 * @since 7.3
 */
@Incubating
@HasInternalProtocol
public interface JavaToolchainProvisioningDetails {
    /**
     * Returns the spec of the requested toolchain.
     */
    JavaToolchainSpec getRequested();

    /**
     * Entry point for building new candidates.
     */
    JavaToolchainCandidate.Builder newCandidate();

    /**
     * Called by the provisioner if it can provision JDKs matching the
     * provided {@link #getRequested() toolchain spec}.
     * @param candidates the list of candidates it can supply
     */
    void listCandidates(List<JavaToolchainCandidate> candidates);

    /**
     * Returns the operating system of the current build. One of windows, mac, linux, solaris or unknown.
     */
    String getOperatingSystem();

    /**
     * Returns the architecture of the system. One of x32, x64, aarch64 or any value returned by the system.
     */
    String getSystemArch();
}
