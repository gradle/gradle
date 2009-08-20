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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolvedDependency;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedArtifactTest {
    @Test
    public void init() {
        String someName = "someName";
        String someType = "someType";
        String someExtension = "someExtension";
        File someFile = new File("someFile");
        DefaultResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(
                someName, someType, someExtension, someFile);
        assertThat(resolvedArtifact.getResolvedDependency(), nullValue());
        assertThat(resolvedArtifact.getName(), equalTo(someName));
        assertThat(resolvedArtifact.getType(), equalTo(someType));
        assertThat(resolvedArtifact.getExtension(), equalTo(someExtension));
        assertThat(resolvedArtifact.getFile(), equalTo(someFile));
        assertThat(resolvedArtifact.getVersion(), nullValue());
        assertThat(resolvedArtifact.getDependencyName(), nullValue());
    }

    @Test
    public void setResolvedDependency() {
        final String someVersion = "someVersion";
        final String someDependencyName = "someDependencyName";
        JUnit4Mockery context = new JUnit4Mockery();
        final ResolvedDependency resolvedDependencyStub = context.mock(ResolvedDependency.class);
        context.checking(new Expectations() {{
            allowing(resolvedDependencyStub).getVersion();
            will(returnValue(someVersion));
            allowing(resolvedDependencyStub).getName();
            will(returnValue(someDependencyName));
        }});
        DefaultResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact("someName", "someType", "someExtension", new File("someFile"));
        resolvedArtifact.setResolvedDependency(resolvedDependencyStub);
        assertThat(resolvedArtifact.getResolvedDependency(), sameInstance(resolvedDependencyStub));
        assertThat(resolvedArtifact.getVersion(), equalTo(someVersion));
        assertThat(resolvedArtifact.getDependencyName(), equalTo(someDependencyName));
    }
}
