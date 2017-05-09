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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.tasks.TaskDependency;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.gradle.util.WrapUtil.toLinkedSet;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultSelfResolvingDependencyTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final FileCollectionInternal source = context.mock(FileCollectionInternal.class);
    private final ComponentIdentifier targetComponent = context.mock(ComponentIdentifier.class);
    private final DefaultSelfResolvingDependency dependency = new DefaultSelfResolvingDependency(targetComponent, source);

    @Test
    public void defaultValues() {
        assertThat(dependency.getGroup(), nullValue());
        assertThat(dependency.getName(), equalTo("unspecified"));
        assertThat(dependency.getVersion(), nullValue());
    }

    @Test
    public void resolvesToTheSourceFileCollection() {
        final DependencyResolveContext resolveContext = context.mock(DependencyResolveContext.class);

        context.checking(new Expectations() {{
            oneOf(resolveContext).add(source);
        }});

        dependency.resolve(resolveContext);
    }

    @Test
    public void usesSourceFileCollectionToResolveFiles() {
        final File file = new File("file");

        context.checking(new Expectations(){{
            allowing(source).getFiles();
            will(returnValue(toSet(file)));
        }});

        assertThat(dependency.resolve(), equalTo(toLinkedSet(file)));
        assertThat(dependency.resolve(true), equalTo(toLinkedSet(file)));
        assertThat(dependency.resolve(false), equalTo(toLinkedSet(file)));
    }

    @Test
    public void createsCopy() {
        DefaultSelfResolvingDependency copy = dependency.copy();
        assertThat(copy, instanceOf(DefaultSelfResolvingDependency.class));
        assertThat(copy.getTargetComponentId(), equalTo(targetComponent));
        assertThat(copy.getFiles(), sameInstance((Object) source));

        assertTrue(copy.contentEquals(dependency));
        assertTrue(dependency.contentEquals(copy));
    }

    @Test
    public void contentsAreEqualWhenFileSetsAreEqual() {
        SelfResolvingDependency equalDependency = new DefaultSelfResolvingDependency(source);
        SelfResolvingDependency differentSource = new DefaultSelfResolvingDependency(context.mock(FileCollectionInternal.class, "other"));
        Dependency differentType = context.mock(Dependency.class);

        assertTrue(dependency.contentEquals(dependency));
        assertTrue(dependency.contentEquals(equalDependency));
        assertFalse(dependency.contentEquals(differentSource));
        assertFalse(dependency.contentEquals(differentType));
    }

    @Test
    public void usesSourceFileCollectionToDetermineBuildDependencies() {
        final TaskDependency taskDependency = context.mock(TaskDependency.class);

        context.checking(new Expectations() {{
            allowing(source).getBuildDependencies();
            will(returnValue(taskDependency));
        }});

        assertThat(dependency.getBuildDependencies(), sameInstance(taskDependency));
    }
}
