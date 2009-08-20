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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.*;
import org.gradle.api.specs.Specs;
import static org.gradle.util.WrapUtil.toLinkedSet;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

@RunWith(JMock.class)
public class SelfResolvingDependencyResolverTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final IvyDependencyResolver delegate = context.mock(IvyDependencyResolver.class);
    private final ResolvedConfiguration resolvedConfiguration = context.mock(ResolvedConfiguration.class);
    private final Configuration configuration = context.mock(Configuration.class);
    private final Ivy ivy = Ivy.newInstance();
    private final ModuleDescriptor moduleDescriptor = context.mock(ModuleDescriptor.class);

    private final SelfResolvingDependencyResolver resolver = new SelfResolvingDependencyResolver(delegate);

    @Test
    public void wrapsResolvedConfigurationProvidedByDelegate() {
        context.checking(new Expectations() {{
            one(delegate).resolve(configuration, ivy, moduleDescriptor);
            will(returnValue(resolvedConfiguration));
            allowing(configuration).getAllDependencies(SelfResolvingDependency.class);
            will(returnValue(toSet()));
        }});

        ResolvedConfiguration configuration = resolver.resolve(this.configuration, ivy, moduleDescriptor);
        assertThat(configuration, not(sameInstance(resolvedConfiguration)));

        final File file = new File("file");

        context.checking(new Expectations() {{
            one(resolvedConfiguration).getFiles(Specs.SATISFIES_ALL);
            will(returnValue(toSet(file)));
        }});

        assertThat(configuration.getFiles(Specs.SATISFIES_ALL), equalTo(toLinkedSet(file)));
    }
    
    @Test
    public void addsFilesFromSelfResolvingDependenciesBeforeFilesFromResolvedConfiguration() {
        final SelfResolvingDependency dependency = context.mock(SelfResolvingDependency.class);

        context.checking(new Expectations() {{
            one(delegate).resolve(configuration, ivy, moduleDescriptor);
            will(returnValue(resolvedConfiguration));
            allowing(configuration).getAllDependencies(SelfResolvingDependency.class);
            will(returnValue(toSet(dependency)));
        }});

        ResolvedConfiguration configuration = resolver.resolve(this.configuration, ivy, moduleDescriptor);
        assertThat(configuration, not(sameInstance(resolvedConfiguration)));

        final File configFile = new File("from config");
        final File depFile = new File("from dep");

        context.checking(new Expectations() {{
            one(resolvedConfiguration).getFiles(Specs.SATISFIES_ALL);
            will(returnValue(toSet(configFile)));
            one(dependency).resolve();
            will(returnValue(toSet(depFile)));
        }});

        assertThat(configuration.getFiles(Specs.SATISFIES_ALL), equalTo(toLinkedSet(depFile, configFile)));
    }

    @Test
    public void testGetModuleDependencies() throws IOException, ParseException {
        context.checking(new Expectations() {{
            one(delegate).resolve(configuration, ivy, moduleDescriptor);
            will(returnValue(resolvedConfiguration));
            allowing(configuration).getAllDependencies(SelfResolvingDependency.class);
            will(returnValue(toSet()));
        }});

        final ResolvedDependency resolvedDependency = context.mock(ResolvedDependency.class);

        context.checking(new Expectations() {{
            one(resolvedConfiguration).getFirstLevelModuleDependencies();
            will(returnValue(toSet(resolvedDependency)));
        }});

        assertThat(resolver.resolve(this.configuration, ivy, moduleDescriptor).getFirstLevelModuleDependencies(),
                equalTo(toSet(resolvedDependency)));
    }

    @Test
    public void testGetResolvedArtifacts() {
        context.checking(new Expectations() {{
            one(delegate).resolve(configuration, ivy, moduleDescriptor);
            will(returnValue(resolvedConfiguration));
            allowing(configuration).getAllDependencies(SelfResolvingDependency.class);
            will(returnValue(toSet()));
        }});

        final ResolvedArtifact resolvedArtifact = context.mock(ResolvedArtifact.class);

        context.checking(new Expectations() {{
            one(resolvedConfiguration).getResolvedArtifacts();
            will(returnValue(toSet(resolvedArtifact)));
        }});

        assertThat(resolver.resolve(this.configuration, ivy, moduleDescriptor).getResolvedArtifacts(),
                equalTo(toSet(resolvedArtifact)));
    }

}
