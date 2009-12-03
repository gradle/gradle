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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.file.FileCollection;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class SelfResolvingDependencyFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final SelfResolvingDependencyFactory factory = new SelfResolvingDependencyFactory(new AsmBackedClassGenerator());

    @Test
    public void createsADependencyFromAFileCollectionNotation() {
        FileCollection collection = context.mock(FileCollection.class);

        Dependency dependency = factory.createDependency(Dependency.class, collection);
        assertThat(dependency, instanceOf(DefaultSelfResolvingDependency.class));
        DefaultSelfResolvingDependency selfResolvingDependency = (DefaultSelfResolvingDependency) dependency;
        assertThat(selfResolvingDependency.getSource(), sameInstance(collection));
    }

    @Test(expected = IllegalDependencyNotation.class)
    public void throwsExceptionForOtherNotation() {
        factory.createDependency(Dependency.class, "something else");
    }
}
