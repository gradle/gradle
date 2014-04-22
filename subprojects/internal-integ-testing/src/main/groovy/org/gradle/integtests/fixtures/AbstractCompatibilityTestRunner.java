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

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution;
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GradleVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.gradle.util.CollectionUtils.*;

/**
 * A base class for those test runners which execute a test multiple times against a set of Gradle versions.
 */
public abstract class AbstractCompatibilityTestRunner extends AbstractMultiTestRunner {

    private static final String VERSIONS_SYSPROP_NAME = "org.gradle.integtest.versions";
    protected final GradleDistribution current = new UnderDevelopmentGradleDistribution();
    protected final List<GradleDistribution> previous;
    protected final boolean implicitVersion;

    protected AbstractCompatibilityTestRunner(Class<?> target) {
        this(target, System.getProperty(VERSIONS_SYSPROP_NAME, "latest"));
    }

    private AbstractCompatibilityTestRunner(Class<?> target, String versionStr) {
        super(target);
        validateTestName(target);

        previous = new ArrayList<GradleDistribution>();
        final ReleasedVersionDistributions previousVersions = new ReleasedVersionDistributions();
        if (versionStr.equals("latest")) {
            previous.add(previousVersions.getMostRecentFinalRelease());
            implicitVersion = true;
        } else if (versionStr.equals("all")) {
            implicitVersion = true;
            List<GradleDistribution> all = previousVersions.getAll();
            for (GradleDistribution previous : all) {
                if (!previous.worksWith(Jvm.current())) {
                    add(new IgnoredVersion(previous, "does not work with current JVM"));
                    continue;
                }
                if (!previous.worksWith(OperatingSystem.current())) {
                    add(new IgnoredVersion(previous, "does not work with current OS"));
                    continue;
                }
                this.previous.add(previous);
            }
        } else if (versionStr.matches("^\\d.*$")) {
            implicitVersion = false;
            String[] versions = versionStr.split(",");
            List<GradleVersion> gradleVersions = CollectionUtils.sort(collect(Arrays.asList(versions), new Transformer<GradleVersion, String>() {
                public GradleVersion transform(String versionString) {
                    return GradleVersion.version(versionString);
                }
            }), Collections.reverseOrder());

            inject(previous, gradleVersions, new Action<InjectionStep<List<GradleDistribution>, GradleVersion>>() {
                public void execute(InjectionStep<List<GradleDistribution>, GradleVersion> step) {
                    GradleDistribution distribution = previousVersions.getDistribution(step.getItem());
                    if (distribution == null) {
                        throw new RuntimeException("Gradle version '" + step.getItem().getVersion() + "' is not a valid testable released version");
                    }
                    step.getTarget().add(distribution);
                }
            });
        } else {
            throw new RuntimeException("Invalid value for " + VERSIONS_SYSPROP_NAME + " system property: " + versionStr + "(valid values: 'all', 'latest' or comma separated list of versions)");
        }
    }

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
