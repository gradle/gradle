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

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.FrameworkMethod;
import static org.junit.Assert.*;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

public class DistributionIntegrationTestRunner extends BlockJUnit4ClassRunner {
    private static GradleDistribution dist;

    public DistributionIntegrationTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
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
        injectValue(target, new ForkingGradleExecuter(distribution), GradleExecuter.class);
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

    private static GradleDistribution getDist() throws IOException {
        if (dist == null) {
            final File gradleHomeDir = file("integTest.gradleHomeDir");
            final File samplesDir = new File(gradleHomeDir, "samples");
            File srcDir = file("integTest.srcDir");
            final File userGuideOutputDir = new File(srcDir, "samples/userguideOutput");
            final File userGuideInfoDir = file("integTest.userGuideInfoDir");

            dist = new GradleDistribution() {
                public File getGradleHomeDir() {
                    return gradleHomeDir;
                }

                public File getSamplesDir() {
                    return samplesDir;
                }

                public File getUserGuideInfoDir() {
                    return userGuideInfoDir;
                }

                public File getUserGuideOutputDir() {
                    return userGuideOutputDir;
                }
            };
        }
        return dist;
    }

    private static File file(String propertyName) {
        String path = System.getProperty(propertyName);
        assertNotNull(String.format("Property '%s' not defined.", propertyName), path);
        try {
            return new File(path).getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
