/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import org.gradle.internal.UncheckedException;

import java.net.URI;
import java.net.URISyntaxException;

public class DistributionLocator {
    private static final String RELEASE_REPOSITORY = "https://services.gradle.org/distributions";
    private static final String SNAPSHOT_REPOSITORY = "https://services.gradle.org/distributions-snapshots";

    public URI getDistributionFor(GradleVersion version) {
        return getDistributionFor(version, "bin");
    }

    public URI getDistributionFor(GradleVersion version, String type) {
        return getDistribution(getDistributionRepository(version), version, "gradle", type);
    }

    private String getDistributionRepository(GradleVersion version) {
        if (version.isSnapshot()) {
            return SNAPSHOT_REPOSITORY;
        } else {
            return RELEASE_REPOSITORY;
        }
    }

    private URI getDistribution(String repositoryUrl, GradleVersion version, String archiveName,
                                   String archiveClassifier) {
        try {
            return new URI(repositoryUrl + "/" + archiveName + "-" + version.getVersion() + "-" + archiveClassifier + ".zip");
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
