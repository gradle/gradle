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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.plugins.jvm.JvmTestingFramework;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

/**
 * {@link JvmTestingFramework} which will execute using Junit 5 (Jupiter).
 *
 * @since 7.3
 */
@Incubating
@NonNullApi
public abstract class JunitPlatformTestingFramework extends AbstractJvmTestingFramework {
    private static final String DEFAULT_VERSION = "5.7.2";

    @Inject
    public JunitPlatformTestingFramework(Project project) {
        super(project);
        version.convention(DEFAULT_VERSION);
    }

    @Override
    public List<Dependency> getImplementationDependencies() {
        return Collections.singletonList(project.getDependencies().create("org.junit.jupiter:junit-jupiter-api:" + version.get()));
    }

    @Override
    public List<Dependency> getRuntimeOnlyDependencies() {
        return Collections.singletonList(project.getDependencies().create("org.junit.jupiter:junit-jupiter-engine:" + version.get()));
    }

    @Override
    public TestFramework getTestFramework(Test test) {
        return new JUnitPlatformTestFramework((DefaultTestFilter) test.getFilter());
    }
}
