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
import org.gradle.api.file.FileCollection;
import static org.gradle.util.Matchers.*;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(JMock.class)
public class DefaultSelfResolvingDependencyTest {
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void defaultValues() {
        SelfResolvingDependency dependency = new DefaultSelfResolvingDependency(context.mock(FileCollection.class));

        assertThat(dependency.getGroup(), nullValue());
        assertThat(dependency.getName(), equalTo("unspecified"));
        assertThat(dependency.getVersion(), nullValue());
    }

    @Test
    public void resolve() {
        final File file = new File("file");

        final FileCollection source = context.mock(FileCollection.class);
        SelfResolvingDependency dependency = new DefaultSelfResolvingDependency(source);

        context.checking(new Expectations(){{
            one(source).getFiles();
            will(returnValue(toSet(file)));
        }});

        assertThat(dependency.resolve(), equalTo(toLinkedSet(file)));
    }

    @Test
    public void createsCopy() {
        SelfResolvingDependency dependency = new DefaultSelfResolvingDependency(context.mock(FileCollection.class));

        Dependency copy = dependency.copy();
        assertThat(copy, instanceOf(SelfResolvingDependency.class));
        assertTrue(copy.contentEquals(dependency));
        assertTrue(dependency.contentEquals(copy));
    }

    @Test
    public void contentsAreEqualWhenFileSetsAreEqual() {
        FileCollection source = context.mock(FileCollection.class);
        SelfResolvingDependency dependency = new DefaultSelfResolvingDependency(source);
        SelfResolvingDependency equalDependency = new DefaultSelfResolvingDependency(source);
        SelfResolvingDependency differentSource = new DefaultSelfResolvingDependency(context.mock(FileCollection.class, "other"));
        Dependency differentType = context.mock(Dependency.class);

        assertTrue(dependency.contentEquals(dependency));
        assertTrue(dependency.contentEquals(equalDependency));
        assertFalse(dependency.contentEquals(differentSource));
        assertFalse(dependency.contentEquals(differentType));
    }

    @Test
    public void canGetAndSetBuildTaskDependencies() {
        DefaultSelfResolvingDependency dependency = new DefaultSelfResolvingDependency(context.mock(FileCollection.class));

        assertThat(dependency.getBuiltBy(), isEmpty());
        dependency.builtBy("a", "b");
        assertThat(dependency.getBuiltBy(), equalTo(toSet((Object) "a", "b")));
        dependency.setBuiltBy(toList("c"));
        assertThat(dependency.getBuiltBy(), equalTo(toSet((Object) "c")));
    }
}
