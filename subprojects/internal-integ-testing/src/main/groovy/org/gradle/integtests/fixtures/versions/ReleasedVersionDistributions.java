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

import java.util.List;
import java.util.Properties;

import static org.gradle.util.CollectionUtils.findFirst;

/**
 * Provides access to {@link GradleDistribution}s for versions of Gradle that have been released.
 *
 * Only versions that are suitable for testing against are made available.
 */
public class ReleasedVersionDistributions {

    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext();

    private final Factory<Properties> versionsFactory;
    private Properties properties;
    private List<GradleDistribution> distributions;

    public ReleasedVersionDistributions() {
        this(new ClasspathVersionSource());
    }

    ReleasedVersionDistributions(Factory<Properties> versionsFactory) {
        this.versionsFactory = versionsFactory;
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

    public GradleDistribution getDistribution(final GradleVersion gradleVersion) {
        return findFirst(getAll(), new Spec<GradleDistribution>() {
            public boolean isSatisfiedBy(GradleDistribution element) {
                return element.getVersion().equals(gradleVersion);
            }
        });
    }
}
