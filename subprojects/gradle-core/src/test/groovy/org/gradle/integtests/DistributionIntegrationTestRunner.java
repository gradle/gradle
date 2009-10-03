/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests;

import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

public class DistributionIntegrationTestRunner extends BlockJUnit4ClassRunner {
    private static final String NOFORK_SYS_PROP = "org.gradle.integtest.nofork";
    private static final String IGNORE_SYS_PROP = "org.gradle.integtest.ignore";

    public DistributionIntegrationTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    public void run(final RunNotifier notifier) {
        if (System.getProperty(IGNORE_SYS_PROP) != null) {
            notifier.fireTestIgnored(Description.createTestDescription(getTestClass().getJavaClass(),
                    "System property to ignore integration tests is set."));
        } else {
            super.run(notifier);
        }
    }

    @Override
    protected Statement withBefores(FrameworkMethod method, final Object target, Statement statement) {
        final Statement setup = super.withBefores(method, target, statement);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                attachDistribution(target);
                setup.evaluate();
            }
        };
    }

    private void attachDistribution(Object target) throws Exception {
        GradleDistribution distribution = getDist();
        injectValue(target, distribution, GradleDistribution.class);
        boolean noFork = System.getProperty(NOFORK_SYS_PROP) != null;
        GradleExecuter executer = noFork ? new QuickGradleExecuter(distribution) : new ForkingGradleExecuter(distribution);
        injectValue(target, executer, GradleExecuter.class);
    }

    private void injectValue(Object target, Object value, Class<?> type) throws IllegalAccessException {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().equals(type)) {
                    field.setAccessible(true);
                    field.set(target, value);
                }
            }
        }
    }

    private GradleDistribution getDist() throws IOException {
        final TestFile userHomeDir = file("integTest.gradleUserHomeDir", "intTestHomeDir");
        final TestFile gradleHomeDir = file("integTest.gradleHomeDir", null);
        TestFile samplesDir = new TestFile(gradleHomeDir, "samples");
        if (!samplesDir.exists()) {
            samplesDir = new TestFile(new File("subprojects/gradle-docs/build/samples"));
        }
        final TestFile samples = samplesDir;
        final TestFile userGuideOutputDir = file("integTest.userGuideOutputDir", "subprojects/gradle-docs/src/samples/userguideOutput");
        final TestFile userGuideInfoDir = file("integTest.userGuideInfoDir", "subprojects/gradle-docs/build/docbook/src");
        final TestFile distsDir = file("integTest.distsDir", "build/distributions");
        final TestFile testDir = new TestFile(GFileUtils.canonicalise(HelperUtil.makeNewTestDir()));

        return new GradleDistribution() {
            public boolean isFileUnderTest(File file) {
                return gradleHomeDir.isSelfOrDescendent(file)
                        || samples.isSelfOrDescendent(file)
                        || testDir.isSelfOrDescendent(file)
                        || userHomeDir.isSelfOrDescendent(file);
            }

            public TestFile getUserHomeDir() {
                return userHomeDir;
            }

            public TestFile getGradleHomeDir() {
                return gradleHomeDir;
            }

            public TestFile getSamplesDir() {
                return samples;
            }

            public TestFile getUserGuideInfoDir() {
                return userGuideInfoDir;
            }

            public TestFile getUserGuideOutputDir() {
                return userGuideOutputDir;
            }

            public TestFile getDistributionsDir() {
                return distsDir;
            }

            public TestFile getTestDir() {
                return testDir;
            }

            public TestFile testFile(Object... path) {
                return testDir.file(path);
            }
        };
    }

    private static TestFile file(String propertyName, String defaultFile) {
        String path = System.getProperty(propertyName, defaultFile);
        if (path == null) {
            throw new RuntimeException(String.format("You must set the '%s' property to run the integration tests.",
                    propertyName));
        }
        return new TestFile(new File(path));
    }
}
