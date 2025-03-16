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

package org.gradle.integtests.fixtures.compatibility;

import org.gradle.integtests.fixtures.GradleDistributionTool;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext;
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution;
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.internal.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.gradle.util.internal.CollectionUtils.sort;

public abstract class AbstractCompatibilityTestInterceptor extends AbstractContextualMultiVersionTestInterceptor<GradleDistributionTool> {
    protected final IntegrationTestBuildContext buildContext = IntegrationTestBuildContext.INSTANCE;
    final ReleasedVersionDistributions releasedVersions = new ReleasedVersionDistributions(buildContext);
    protected final GradleDistribution current = new UnderDevelopmentGradleDistribution(buildContext);

    protected AbstractCompatibilityTestInterceptor(Class<?> target) {
        super(target);
        validateTestName(target);
    }

    @Override
    protected Collection<GradleDistributionTool> getQuickVersions() {
        return Collections.singleton(versionedToolFrom(releasedVersions.getMostRecentRelease()));
    }

    @Override
    protected Collection<GradleDistributionTool> getAllVersions() {
        List<GradleDistribution> allSupportedVersions = choosePreviousVersionsToTest(releasedVersions);
        List<GradleDistribution> sortedDistributions = sort(allSupportedVersions, Comparator.comparing(GradleDistribution::getVersion));
        return CollectionUtils.collect(sortedDistributions, this::versionedToolFrom);
    }

    @Override
    protected boolean isAvailable(GradleDistributionTool version) {
        return true;
    }

    @Override
    protected Collection<Execution> createExecutionsFor(GradleDistributionTool versionedTool) {
        if (versionedTool.getIgnored() != null) {
            return Collections.singleton(new IgnoredVersion(versionedTool.getDistribution(), versionedTool.getIgnored()));
        } else {
            return createDistributionExecutionsFor(versionedTool);
        }
    }

    private GradleDistributionTool versionedToolFrom(GradleDistribution distribution) {
        if (!distribution.worksWith(Jvm.current())) {
            return new GradleDistributionTool(distribution, "does not work with current JVM");
        } else if (!distribution.worksWith(OperatingSystem.current())) {
            return new GradleDistributionTool(distribution, "does not work with current OS");
        } else {
            return new GradleDistributionTool(distribution);
        }
    }

    protected abstract List<GradleDistribution> choosePreviousVersionsToTest(ReleasedVersionDistributions previousVersions);

    protected abstract Collection<Execution> createDistributionExecutionsFor(GradleDistributionTool versionedTool);

    /**
     * Makes sure the test adheres to the naming convention.
     *
     * @param target test class
     */
    private void validateTestName(Class<?> target) {
        if (!target.getSimpleName().contains("CrossVersion")) {
            throw new RuntimeException("The tests that use " + this.getClass().getSimpleName()
                + " must follow a certain naming convention, e.g. name must contain 'CrossVersion' substring.\n"
                + "This way we can include/exclude those test nicely and it is easier to configure the CI.\n"
                + "Please include 'CrossVersion' in the name of the test: '" + target.getSimpleName() + "'");
        }
    }

    private static class IgnoredVersion extends Execution {
        private final GradleDistribution distribution;
        private final String why;

        private IgnoredVersion(GradleDistribution distribution, String why) {
            this.distribution = distribution;
            this.why = why;
        }

        @Override
        public boolean isTestEnabled(TestDetails testDetails) {
            return false;
        }

        @Override
        protected String getDisplayName() {
            return String.format("%s %s", distribution.getVersion(), why);
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }
}
