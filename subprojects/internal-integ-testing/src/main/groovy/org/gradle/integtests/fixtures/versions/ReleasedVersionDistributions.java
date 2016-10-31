/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.versions;

import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext;
import org.gradle.internal.Factory;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GradleVersion;

import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import static org.gradle.util.CollectionUtils.findFirst;
import static org.gradle.util.CollectionUtils.sort;

/**
 * Provides access to {@link GradleDistribution}s for versions of Gradle that have been released.
 *
 * Only versions that are suitable for testing against are made available.
 */
public class ReleasedVersionDistributions {

    private final IntegrationTestBuildContext buildContext;

    private final Factory<Properties> versionsFactory;
    private Properties properties;
    private List<GradleDistribution> distributions;

    public ReleasedVersionDistributions() {
        this(IntegrationTestBuildContext.INSTANCE);
    }

    public ReleasedVersionDistributions(IntegrationTestBuildContext buildContext) {
        this(new ClasspathVersionSource(), buildContext);
    }

    ReleasedVersionDistributions(Factory<Properties> versionsFactory, IntegrationTestBuildContext buildContext) {
        this.versionsFactory = versionsFactory;
        this.buildContext = buildContext;
    }

    private Properties getProperties() {
        if (properties == null) {
            properties = versionsFactory.create();
        }

        return properties;
    }

    public GradleDistribution getMostRecentFinalRelease() {
        String mostRecentFinal = getProperties().getProperty("mostRecent");

        if (mostRecentFinal == null) {
            throw new RuntimeException("Unable to get the last version");
        }

        return buildContext.distribution(mostRecentFinal);
    }

    public GradleDistribution getMostRecentSnapshot() {
        String mostRecentSnapshot = getProperties().getProperty("mostRecentSnapshot");

        if (mostRecentSnapshot == null) {
            throw new RuntimeException("Unable to get the last snapshot version");
        }

        return buildContext.distribution(mostRecentSnapshot);
    }

    public List<GradleDistribution> getAll() {
        if (distributions == null) {
            distributions = CollectionUtils.collect(getProperties().getProperty("versions").split("\\s+"), new Transformer<GradleDistribution, String>() {
                public GradleDistribution transform(String version) {
                    return buildContext.distribution(version);
                }
            });
        }
        return distributions;
    }

    public List<GradleDistribution> getSupported() {
        final GradleVersion firstSupported = GradleVersion.version("1.0");
        return CollectionUtils.filter(getAll(), new Spec<GradleDistribution>() {
            @Override
            public boolean isSatisfiedBy(GradleDistribution element) {
                return element.getVersion().compareTo(firstSupported) >= 0;
            }
        });
    }

    public GradleDistribution getDistribution(final GradleVersion gradleVersion) {
        return findFirst(getAll(), new Spec<GradleDistribution>() {
            public boolean isSatisfiedBy(GradleDistribution element) {
                return element.getVersion().equals(gradleVersion);
            }
        });
    }

    public GradleDistribution getDistribution(final String gradleVersion) {
        return findFirst(getAll(), new Spec<GradleDistribution>() {
            public boolean isSatisfiedBy(GradleDistribution element) {
                return element.getVersion().getVersion().equals(gradleVersion);
            }
        });
    }

    public GradleDistribution getPrevious(final GradleVersion gradleVersion) {
        GradleDistribution distribution = getDistribution(gradleVersion);
        List<GradleDistribution> sortedDistributions = sort(distributions, new Comparator<GradleDistribution>() {
            public int compare(GradleDistribution dist1, GradleDistribution dist2) {
                return dist1.getVersion().compareTo(dist2.getVersion());
            }
        });
        int distributionIndex = sortedDistributions.indexOf(distribution) - 1;
        return distributionIndex >= 0 ? sortedDistributions.get(distributionIndex) : null;
    }
}
