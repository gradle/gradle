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

import org.gradle.integtests.fixtures.executer.GradleBuiltDistribution;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A base class for those test runners which execute a test multiple times against a set of Gradle versions.
 */
public abstract class AbstractCompatibilityTestRunner extends AbstractMultiTestRunner {

    private final TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider();
    protected final GradleDistribution current = new GradleBuiltDistribution(testDirectoryProvider);
    protected final List<GradleDistribution> previous;

    protected AbstractCompatibilityTestRunner(Class<?> target) {
        this(target, null);
    }

    protected AbstractCompatibilityTestRunner(Class<?> target, String versionStr) {
        super(target);
        validateTestName(target);

        previous = new ArrayList<GradleDistribution>();
        if (versionStr == null) {
            versionStr = System.getProperty("org.gradle.integtest.versions", "latest");
        }
        ReleasedVersions previousVersions = new ReleasedVersions(testDirectoryProvider);
        if (!versionStr.equals("all")) {
            previous.add(previousVersions.getLast());
        } else {
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
        protected boolean isEnabled() {
            return false;
        }

        @Override
        protected String getDisplayName() {
            return String.format("%s %s", distribution.getVersion(), why);
        }
    }
}
