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

import java.io.File;
import java.util.List;

/**
 * Base interface for toolchain provisioners. Plugin authors may implement
 * additional provisioners by implementing this interface.
 *
 * @since 7.3
 */
@Incubating
public interface JavaToolchainProvisioningService {
    /**
     * Called by Gradle on provisioners when it needs to provision a JDK.
     * The responsibility of the provisioner is to list the candidates matching
     * the request. For this, provisioners need to look into the details object
     * and call the {@link JavaToolchainProvisioningDetails#listCandidates(List)}
     * method if they can provide a requested JDK.
     *
     * @param details context of the JDK query
     */
    void findCandidates(JavaToolchainProvisioningDetails details);

    /**
     * When a candidate is selected, it is the responsibility of implementors
     * to provide a provisioner which is going to download the JDK archive.
     * This provisioner must provide the name of the archive without triggering
     * provisioning (download) itself.
     *
     * @param candidate the candidate for which to build a provisioner
     * @return a provisioner
     */
    LazyProvisioner provisionerFor(JavaToolchainCandidate candidate);

    /**
     * A provisioner is typically responsible for downloading a JDK archive.
     *
     * @since 7.3
     */
    @Incubating
    interface LazyProvisioner {
        /**
         * Returns the name of the archive file representing a JDK.
         */
        String getFileName();

        /**
         * Performs provisioning. The archive MUST be written at the supplied
         * location.
         *
         * @param destination the destination archive file
         * @return true if provisioning was successful
         */
        boolean provision(File destination);
    }
}
