/*
 * Copyright 2007-2009 the original author or authors.
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

import org.apache.ivy.core.report.ResolveReport;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultModuleDescriptorConverter;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyServiceTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    // SUT
    private DefaultIvyService ivyService;
    private DependencyMetaDataProvider dependencyMetaDataProvider;

    @Before
    public void setUp() {
        dependencyMetaDataProvider = context.mock(DependencyMetaDataProvider.class);
        ivyService = new DefaultIvyService(dependencyMetaDataProvider);
    }

    @Test
    public void init() {
        assertThat(ivyService.getMetaDataProvider(), sameInstance(dependencyMetaDataProvider));
        assertThat(ivyService.getSettingsConverter(), instanceOf(DefaultSettingsConverter.class));
        assertThat(ivyService.getModuleDescriptorConverter(), instanceOf(DefaultModuleDescriptorConverter.class));
        assertThat(ivyService.getIvyFactory(), instanceOf(DefaultIvyFactory.class));
        assertThat(ivyService.getDependencyResolver(), instanceOf(DefaultIvyDependencyResolver.class));
        assertThat(ivyService.getDependencyPublisher(), instanceOf(DefaultIvyDependencyPublisher.class));
    }

    @Test
    public void testResolveFromReport() {
        final IvyDependencyResolver ivyDependencyResolverMock = context.mock(IvyDependencyResolver.class);
        ivyService.setDependencyResolver(ivyDependencyResolverMock);

        final Configuration configurationDummy = context.mock(Configuration.class);
        final ResolveReport resolveReportDummy = context.mock(ResolveReport.class);
        final Set<File> classpathDummy = WrapUtil.toSet(new File("cp"));
        context.checking(new Expectations() {{
            allowing(ivyDependencyResolverMock).resolveFromReport(configurationDummy, resolveReportDummy);
            will(returnValue(classpathDummy));
        }});
        assertThat(ivyService.resolveFromReport(configurationDummy, resolveReportDummy), equalTo(classpathDummy));
    }
}
