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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.testing.base.TestSuite;

/**
 * A group of related tests which both distinguish tests used for different purposes and which
 * can be configured to run against repeatedly against multiple {@link JvmTestSuiteTarget}s.
 *
 * @since 7.3
 */
@Incubating
public interface JvmTestSuite extends TestSuite, Buildable {
    SourceSet getSources();
    void sources(Action<? super SourceSet> configuration);

    ExtensiblePolymorphicDomainObjectContainer<? extends JvmTestSuiteTarget> getTargets();

    Property<JvmTestingFramework> getTestingFramework();

    void useJUnitPlatform();
    void useJUnit();

    ComponentDependencies getDependencies();
    void dependencies(Action<? super ComponentDependencies> dependencies);
}
