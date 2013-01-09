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

import java.util.Comparator;
import java.util.List;

import static org.gradle.integtests.fixtures.versions.ReleasedGradleVersion.Type.FINAL;
import static org.gradle.util.CollectionUtils.*;

/**
 * Provides access to {@link GradleDistribution}s for versions of Gradle that have been released.
 *
 * Only versions that are suitable for testing against are made available.
 *
 * @see IsTestableGradleVersionSpec
 */
public class ReleasedVersionDistributions {

    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext();

    private final Factory<List<ReleasedGradleVersion>> versionsFactory;
    private List<ReleasedGradleVersion> versions;

    public ReleasedVersionDistributions() {
        this(new Factory<List<ReleasedGradleVersion>>() {
            public List<ReleasedGradleVersion> create() {
                return sort(
                        filter(
                                new VersionWebServiceJsonParser(new ClasspathVersionJsonSource()).create(),
                                new IsTestableGradleVersionSpec()
                        ),
                        new Comparator<ReleasedGradleVersion>() {
                            public int compare(ReleasedGradleVersion o1, ReleasedGradleVersion o2) {
                                return o2.getVersion().compareTo(o1.getVersion());
                            }
                        }
                );
            }
        });
    }

    ReleasedVersionDistributions(Factory<List<ReleasedGradleVersion>> versionsFactory) {
        this.versionsFactory = versionsFactory;
    }

    private List<ReleasedGradleVersion> getVersions() {
        if (versions == null) {
            versions = versionsFactory.create();
        }

        return versions;
    }

    public GradleDistribution getMostRecentFinalRelease() {
        ReleasedGradleVersion mostRecentFinal = findFirst(getVersions(), new Spec<ReleasedGradleVersion>() {
            public boolean isSatisfiedBy(ReleasedGradleVersion element) {
                return element.getType() == FINAL;
            }
        });

        if (mostRecentFinal == null) {
            throw new RuntimeException("Unable to get the last version");
        }

        return buildContext.distribution(mostRecentFinal.getVersion().getVersion());
    }

    public List<GradleDistribution> getAll() {
        return CollectionUtils.collect(getVersions(), new Transformer<GradleDistribution, ReleasedGradleVersion>() {
            public GradleDistribution transform(ReleasedGradleVersion releasedGradleVersion) {
                return buildContext.distribution(releasedGradleVersion.getVersion().getVersion());
            }
        });
    }
}
