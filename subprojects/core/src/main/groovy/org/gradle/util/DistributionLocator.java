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

public class DistributionLocator {
    private static final String ARTIFACTORY_RELEASE_REPOSITORY = "http://gradle.artifactoryonline.com/gradle/distributions";
    private static final String ARTIFACTORY_SNAPSHOT_REPOSITORY = "http://gradle.artifactoryonline.com/gradle/distributions/gradle-snapshots";
    private static final String CODEHAUS_RELEASE_REPOSITORY = "http://dist.codehaus.org/gradle";
    private static final String CODEHAUS_SNAPSHOT_REPOSITORY = "http://snapshots.dist.codehaus.org/gradle";

    public String getDistributionFor(GradleVersion version) {
        return getDistribution(getDistributionRepository(version), version, "gradle", "bin");
    }

    public String getDistributionRepository(GradleVersion version) {
        if (version.compareTo(GradleVersion.version("0.9")) >= 0) {
            if (version.isSnapshot()) {
                return ARTIFACTORY_SNAPSHOT_REPOSITORY;
            }
            return ARTIFACTORY_RELEASE_REPOSITORY;
        } else {
            if (version.isSnapshot()) {
                return CODEHAUS_SNAPSHOT_REPOSITORY;
            }
            return CODEHAUS_RELEASE_REPOSITORY;
        }
    }

    public String getDistribution(String repositoryUrl, GradleVersion version, String archiveName,
                                  String archiveClassifier) {
        return String.format("%s/%s-%s-%s.zip", repositoryUrl, archiveName, version.getVersion(), archiveClassifier);
    }
}
