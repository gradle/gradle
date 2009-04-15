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

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyService_ResolveFromRepoTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    // Dummies
    private Configuration configurationDummy = context.mock(Configuration.class);
    private Module moduleDummy = context.mock(Module.class);
    private File cacheParentDirDummy = new File("cacheParentDirDummy");
    private Map<String, ModuleDescriptor> clientModuleRegistryDummy = WrapUtil.toMap("a", context.mock(ModuleDescriptor.class));

    private ResolveReport resolveReportDummy = context.mock(ResolveReport.class);;
    private InternalRepository internalRepositoryDummy = context.mock(InternalRepository.class);

    // SUT
    private DefaultIvyService ivyService;

    @Before
    public void setUp() {
        SettingsConverter settingsConverterMock = context.mock(SettingsConverter.class);
        ModuleDescriptorConverter moduleDescriptorConverterMock = context.mock(ModuleDescriptorConverter.class);
        IvyDependencyResolver ivyDependencyResolverMock = context.mock(IvyDependencyResolver.class);
        ivyService = new DefaultIvyService(internalRepositoryDummy);
        ivyService.setSettingsConverter(settingsConverterMock);
        ivyService.setModuleDescriptorConverter(moduleDescriptorConverterMock);
        ivyService.setDependencyResolver(ivyDependencyResolverMock);
    }

    @Test
    public void testResolve() {
        setUp(true);
        final Set<File> classpathDummy = WrapUtil.toSet(new File("cp"));
        context.checking(new Expectations() {{
            allowing(ivyService.getDependencyResolver()).resolveFromReport(configurationDummy, resolveReportDummy);
            will(returnValue(classpathDummy));
        }});
        assertThat(ivyService.resolve(configurationDummy, moduleDummy, cacheParentDirDummy, clientModuleRegistryDummy),
                equalTo(classpathDummy));
    }

    @Test
    public void testResolveAsReport() {
        setUp(false);
        assertThat(ivyService.resolveAsReport(configurationDummy, moduleDummy, cacheParentDirDummy, clientModuleRegistryDummy),
                equalTo(resolveReportDummy));
    }

    private void setUp(final boolean resolveFailOnError) {
        final ModuleDescriptor moduleDescriptorDummy = HelperUtil.createModuleDescriptor(WrapUtil.toSet("someConf"));
        final IvyFactory ivyFactoryStub = context.mock(IvyFactory.class);
        final Ivy ivyStub = context.mock(Ivy.class);
        final Set<Dependency> dependenciesDummy = WrapUtil.toSet(context.mock(Dependency.class));
        final List<DependencyResolver> dependencyResolversDummy = WrapUtil.toList(context.mock(DependencyResolver.class, "dependencies"));
        final IvySettings ivySettingsDummy = new IvySettings();
        context.checking(new Expectations() {{
            allowing(ivyFactoryStub).createIvy(ivySettingsDummy);
            will(returnValue(ivyStub));

            allowing(ivyStub).getSettings();
            will(returnValue(ivySettingsDummy));

            allowing(ivyService.getDependencyResolver()).resolveAsReport(configurationDummy, ivyStub, moduleDescriptorDummy, resolveFailOnError);
            will(returnValue(resolveReportDummy));

            allowing(configurationDummy).getDependencies();
            will(returnValue(dependenciesDummy));

            allowing(configurationDummy).getDependencyResolvers();
            will(returnValue(dependencyResolversDummy));

            allowing(ivyService.getModuleDescriptorConverter()).convertForResolve(configurationDummy, moduleDummy, clientModuleRegistryDummy,
                    ivySettingsDummy);
            will(returnValue(moduleDescriptorDummy));

            allowing(ivyService.getSettingsConverter()).convertForResolve(dependencyResolversDummy, cacheParentDirDummy, internalRepositoryDummy,
                    clientModuleRegistryDummy);
            will(returnValue(ivySettingsDummy));
        }});
        ivyService.setIvyFactory(ivyFactoryStub);
    }
}