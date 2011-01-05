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

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.specs.Specs;
import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.toList;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(JMock.class)
public class ShortcircuitEmptyConfigsIvyServiceTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final IvyService delegate = context.mock(IvyService.class);
    private final Configuration configuration = context.mock(Configuration.class);
    private final ShortcircuitEmptyConfigsIvyService ivyService = new ShortcircuitEmptyConfigsIvyService(delegate);

    @Test
    public void resolveReturnsEmptyResolvedConfigWhenConfigHasNoDependencies() {
        context.checking(new Expectations(){{
            allowing(configuration).getAllDependencies();
            will(returnValue(Collections.emptySet()));
        }});

        ResolvedConfiguration resolvedConfig = ivyService.resolve(configuration);

        assertFalse(resolvedConfig.hasError());
        resolvedConfig.rethrowFailure();
        assertThat(resolvedConfig.getFiles(Specs.<Dependency>satisfyAll()), isEmpty());
        assertThat(resolvedConfig.getFirstLevelModuleDependencies(), isEmpty());
        assertThat(resolvedConfig.getResolvedArtifacts(), isEmpty());
    }

    @Test
    public void resolveDelegatesToBackingServiceWhenConfigHasDependencies() {
        final Dependency dependencyDummy = context.mock(Dependency.class);
        final ResolvedConfiguration resolvedConfigDummy = context.mock(ResolvedConfiguration.class);

        context.checking(new Expectations() {{
            allowing(configuration).getAllDependencies();
            will(returnValue(toSet(dependencyDummy)));

            one(delegate).resolve(configuration);
            will(returnValue(resolvedConfigDummy));
        }});

        assertThat(ivyService.resolve(configuration), sameInstance(resolvedConfigDummy));
    }

    @Test
    public void publishDelegatesToBackingService() {
        final Set<Configuration> configurations = toSet(configuration);
        final File someDescriptorDestination = new File("somePth");
        final List<DependencyResolver> resolvers = toList(context.mock(DependencyResolver.class));

        context.checking(new Expectations(){{
            one(delegate).publish(configurations, someDescriptorDestination, resolvers);
        }});

        ivyService.publish(configurations, someDescriptorDestination, resolvers);
    }
}
