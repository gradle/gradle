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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;

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
        return Collections.singleton(new GradleDistributionTool(releasedVersions.getMostRecentRelease()));
    }

    @Override
    protected Collection<GradleDistributionTool> getAllVersions() {
        return releasedVersions.getSupported().stream()
            .sorted(Comparator.comparing(GradleDistribution::getVersion))
            .map(GradleDistributionTool::new)
            .collect(Collectors.toList());
    }

    @Override
    protected boolean isAvailable(GradleDistributionTool version) {
        return true;
    }

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
}
