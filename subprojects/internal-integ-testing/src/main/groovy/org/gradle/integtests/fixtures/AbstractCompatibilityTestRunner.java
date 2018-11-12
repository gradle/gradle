/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures;

import org.gradle.api.Transformer;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext;
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution;
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GradleVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.sort;

/**
 * A base class for those test runners which execute a test multiple times against a set of Gradle versions.
 */
public abstract class AbstractCompatibilityTestRunner extends AbstractConfigurableMultiVersionSpecRunner {
    protected final IntegrationTestBuildContext buildContext = IntegrationTestBuildContext.INSTANCE;
    final ReleasedVersionDistributions releasedVersions = new ReleasedVersionDistributions(buildContext);
    protected final GradleDistribution current = new UnderDevelopmentGradleDistribution(buildContext);
    protected final List<GradleDistribution> previous = new ArrayList<GradleDistribution>();
    protected boolean implicitVersion;

    protected AbstractCompatibilityTestRunner(Class<?> target) {
        this(target, System.getProperty(VERSIONS_SYSPROP_NAME, "latest"));
    }

    private AbstractCompatibilityTestRunner(Class<?> target, String versionStr) {
        super(target);
        validateTestName(target);
    }

    abstract protected void createConfiguredExecutions();

    @Override
    protected void createExecutionsForContext(CoverageContext context) {
        switch(context) {
            case DEFAULT:
            case LATEST:
                implicitVersion = true;
                addVersionIfCompatibleWithJvmAndOs(releasedVersions.getMostRecentRelease());
                break;
            case PARTIAL:
                implicitVersion = true;
                addVersionIfCompatibleWithJvmAndOs(releasedVersions.getMostRecentRelease());
                addVersionIfCompatibleWithJvmAndOs(getFirstSupportedDistribution(releasedVersions));
                break;
            case FULL:
                implicitVersion = true;
                List<GradleDistribution> previousVersionsToTest = choosePreviousVersionsToTest(releasedVersions);
                for (GradleDistribution previousVersion : previousVersionsToTest) {
                    addVersionIfCompatibleWithJvmAndOs(previousVersion);
                }
                break;
            default:
                throw new RuntimeException("Unhandled coverage context: " + context);
        }

        createConfiguredExecutions();
    }

    @Override
    protected void createSelectedExecutions(List<String> selectionCriteria) {
        List<GradleVersion> gradleVersions = CollectionUtils.sort(collect(selectionCriteria, new Transformer<GradleVersion, String>() {
            public GradleVersion transform(String versionString) {
                return GradleVersion.version(versionString);
            }
        }), Collections.reverseOrder());

        for (GradleVersion gradleVersion : gradleVersions) {
            GradleDistribution distribution = releasedVersions.getDistribution(gradleVersion);
            if (distribution == null) {
                throw new RuntimeException("Gradle version '" + gradleVersion.getVersion() + "' is not a valid testable released version");
            }
            addVersionIfCompatibleWithJvmAndOs(distribution);
        }

        createConfiguredExecutions();
    }

    private GradleDistribution getFirstSupportedDistribution(ReleasedVersionDistributions releasedVersions) {
        List<GradleDistribution> allSupportedVersions = choosePreviousVersionsToTest(releasedVersions);
        List<GradleDistribution> sortedDistributions = sort(allSupportedVersions, new Comparator<GradleDistribution>() {
            public int compare(GradleDistribution dist1, GradleDistribution dist2) {
                return dist1.getVersion().compareTo(dist2.getVersion());
            }
        });
        return sortedDistributions.get(0);
    }

    private void addVersionIfCompatibleWithJvmAndOs(GradleDistribution previousVersion) {
        if (!previousVersion.worksWith(Jvm.current())) {
            add(new IgnoredVersion(previousVersion, "does not work with current JVM"));
        } else if (!previousVersion.worksWith(OperatingSystem.current())) {
            add(new IgnoredVersion(previousVersion, "does not work with current OS"));
        } else {
            this.previous.add(previousVersion);
        }
    }

    protected abstract List<GradleDistribution> choosePreviousVersionsToTest(ReleasedVersionDistributions previousVersions);

    /**
     * Makes sure the test adhers to the naming convention.
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

    public List<GradleDistribution> getPrevious() {
        return previous;
    }

    private static class IgnoredVersion extends Execution {
        private final GradleDistribution distribution;
        private final String why;

        private IgnoredVersion(GradleDistribution distribution, String why) {
            this.distribution = distribution;
            this.why = why;
        }

        @Override
        protected boolean isTestEnabled(TestDetails testDetails) {
            return false;
        }

        @Override
        protected String getDisplayName() {
            return String.format("%s %s", distribution.getVersion(), why);
        }
    }
}
