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
package org.gradle.api.internal.dependencies.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.publish.PublishEngine;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.dependencies.*;
import org.gradle.api.dependencies.specs.DependencyTypeSpec;
import org.gradle.api.dependencies.specs.Type;
import org.gradle.api.internal.dependencies.*;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultIvyHandlerTest {
    private DefaultIvyService ivyHandler;
    private SettingsConverter settingsConverterMock;
    private ModuleDescriptorConverter moduleDescriptorConverterMock;
    private IvyFactory ivyFactoryMock;
    private BuildResolverHandler buildResolverHandlerMock;
    private IvyDependencyResolver ivyDependencyResolverMock;
    private IvyDependencyPublisher ivyDependencyPublisherMock;
    private Configuration configurationMock;

    private List<DependencyResolver> testDependencyResolvers;
    private List<DependencyResolver> testPublishResolvers;
    private DependencyContainerInternal dependencyContainerMock;
    private RepositoryResolver testBuildResolver;
    private File testGradleUserHome;
    private Map<String, ModuleDescriptor> testClientModuleRegistry;
    private IvySettings testIvySettings;
    private Ivy ivyMock;
    private Set<Configuration> testConfigurations;

    private JUnit4Mockery context = new JUnit4Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);  
    }};
    private static final String TEST_CONF = "conf1";
    private ResolveInstruction testResolveInstruction;
    private ModuleDescriptor testModuleDescriptor;
    private List<File> testClasspath;
    private ResolveReport testReport;
    private PublishEngine publishEngineMock;

    @Before
    public void setUp() {
        createMocks();
        initFixture();
        ivyHandler = new DefaultIvyService(settingsConverterMock, moduleDescriptorConverterMock, ivyFactoryMock,
                buildResolverHandlerMock, ivyDependencyResolverMock, ivyDependencyPublisherMock);
    }

    private void createMocks() {
        settingsConverterMock = context.mock(SettingsConverter.class);
        moduleDescriptorConverterMock = context.mock(ModuleDescriptorConverter.class);
        ivyFactoryMock = context.mock(IvyFactory.class);
        buildResolverHandlerMock = context.mock(BuildResolverHandler.class);
        ivyDependencyResolverMock = context.mock(IvyDependencyResolver.class);
        ivyDependencyPublisherMock = context.mock(IvyDependencyPublisher.class);
        dependencyContainerMock = context.mock(DependencyContainerInternal.class);
        ivyMock = context.mock(Ivy.class);
        configurationMock = context.mock(Configuration.class);
        publishEngineMock = context.mock(PublishEngine.class);
    }

    private void initFixture() {
        testDependencyResolvers = WrapUtil.toList(context.mock(DependencyResolver.class, "dependencies"));
        testPublishResolvers = WrapUtil.toList(context.mock(DependencyResolver.class, "publish"));
        testBuildResolver = context.mock(RepositoryResolver.class);
        testGradleUserHome = new File("testGradleUserHome");
        testClientModuleRegistry = WrapUtil.toMap("a", context.mock(ModuleDescriptor.class));
        testIvySettings = new IvySettings();
        testResolveInstruction = new ResolveInstruction().setDependencySpec(new DependencyTypeSpec(Type.EXTERNAL));
        testModuleDescriptor = HelperUtil.getTestModuleDescriptor(WrapUtil.toSet(TEST_CONF));
        testClasspath = WrapUtil.toList(new File("cp"));
        testReport = new ResolveReport(testModuleDescriptor);
        testConfigurations = WrapUtil.toSet(configurationMock);
        context.checking(new Expectations() {{
            allowing(configurationMock).getName();
            will(returnValue(TEST_CONF));
            
            allowing(buildResolverHandlerMock).getBuildResolver();
            will(returnValue(testBuildResolver));

            allowing(dependencyContainerMock).getClientModuleRegistry();
            will(returnValue(testClientModuleRegistry));


            allowing(ivyFactoryMock).createIvy(testIvySettings);
            will(returnValue(ivyMock));
            
            allowing(ivyMock).getPublishEngine();
            will(returnValue(publishEngineMock));

            allowing(ivyDependencyResolverMock).resolveAsReport(TEST_CONF, testResolveInstruction, ivyMock, testModuleDescriptor);
            will(returnValue(testReport));

            allowing(ivyDependencyResolverMock).resolveFromReport(TEST_CONF, testReport);
            will(returnValue(testClasspath));
        }});
    }

    @Test
    public void init() {
        assertThat(ivyHandler.getDependencyResolver(), sameInstance(ivyDependencyResolverMock));
        assertThat(ivyHandler.getBuildResolverHandler(), sameInstance(buildResolverHandlerMock));
        assertThat(ivyHandler.getDependencyPublisher(), sameInstance(ivyDependencyPublisherMock));
        assertThat(ivyHandler.getIvyFactory(), sameInstance(ivyFactoryMock));
        assertThat(ivyHandler.getModuleDescriptorConverter(), sameInstance(moduleDescriptorConverterMock));
        assertThat(ivyHandler.getSettingsConverter(), sameInstance(settingsConverterMock));
    }

    @Test
    public void testIvy() {
        customizeMocks(null, Specs.SATISFIES_ALL, Specs.SATISFIES_ALL, testDependencyResolvers, testPublishResolvers, testClientModuleRegistry, ArtifactContainer.EMPTY_CONTAINER,
                Specs.SATISFIES_ALL, new HashMap<String, Boolean>());
        ivyHandler.ivy(testDependencyResolvers, testPublishResolvers, testGradleUserHome, testClientModuleRegistry);
    }

    @Test
    public void testGetLastResolveReport() {
        context.checking(new Expectations() {{
            allowing(ivyDependencyResolverMock).getLastResolveReport();
            will(returnValue(testReport));
        }});
        assertThat(ivyHandler.getLastResolveReport(), sameInstance(testReport));
    }

    @Test
    public void testResolve() {
        customizeMocksForResolve();
        assertThat(ivyHandler.resolve(TEST_CONF, testConfigurations, dependencyContainerMock, testDependencyResolvers,
                testResolveInstruction, testGradleUserHome),
                equalTo(testClasspath));
    }

    @Test
    public void testResolveFromReport() {
        customizeMocksForResolve();
        assertThat(ivyHandler.resolveFromReport(TEST_CONF, testReport),
                equalTo(testClasspath));
    }

    @Test
    public void testResolveAsReport() {
        customizeMocksForResolve();
        assertThat(ivyHandler.resolveAsReport(TEST_CONF, testConfigurations, dependencyContainerMock, testDependencyResolvers, testResolveInstruction, testGradleUserHome),
                equalTo(testReport));
    }

    private void customizeMocksForResolve() {
        customizeMocks(new DefaultConfigurationContainer(testConfigurations), Specs.SATISFIES_ALL,
                testResolveInstruction.getDependencySpec(), testDependencyResolvers, new ArrayList<DependencyResolver>(), testClientModuleRegistry,
                ArtifactContainer.EMPTY_CONTAINER, Specs.SATISFIES_ALL, WrapUtil.toMap(TEST_CONF, testResolveInstruction.isTransitive()));
    }

    @Test
    public void testPublish() {
        final ConfigurationContainer configurationContainerMock = context.mock(ConfigurationContainer.class);
        final ArtifactContainer artifactContainerMock = context.mock(ArtifactContainer.class);
        final PublishInstruction testPublishInstruction = new PublishInstruction();
        customizeMocks(configurationContainerMock, testPublishInstruction.getModuleDescriptor().getConfigurationSpec(),
                testPublishInstruction.getModuleDescriptor().getDependencySpec(), new ArrayList<DependencyResolver>(), testPublishResolvers, new HashMap<String, ModuleDescriptor>(),
                artifactContainerMock, testPublishInstruction.getArtifactSpec(), new HashMap<String, Boolean>());
        final String testConf2 = "testConf2";
        context.checking(new Expectations() {{
            allowing(configurationContainerMock).get(TEST_CONF);
            will(returnValue(configurationMock));

            allowing(configurationMock).getChain();
            will(returnValue(WrapUtil.toSet(configurationMock, new DefaultConfiguration(testConf2, null))));

            allowing(ivyDependencyPublisherMock).publish(WrapUtil.toSet(TEST_CONF, testConf2),
                    testPublishInstruction, testPublishResolvers, testModuleDescriptor, publishEngineMock);
        }});

        ivyHandler.publish(TEST_CONF, testPublishInstruction, testPublishResolvers, configurationContainerMock,
                dependencyContainerMock, artifactContainerMock, testGradleUserHome);
    }

    private void customizeMocks(final ConfigurationContainer configurationContainer,
                                final Spec<Configuration> moduleDescriptorConfigurationFilter,
                                final Spec<Dependency> moduleDescriptorDependencyFilter,
                                final List<DependencyResolver> settingsConverterDependencyResolvers,
                                final List<DependencyResolver> settingsConverterPublishResolvers,
                                final Map<String, ModuleDescriptor> settingsConverterClientModuleRegistry,
                                final ArtifactContainer moduleConverterArtifactContainer,
                                final Spec<PublishArtifact> moduleDescriptorArtifactFilter,
                                final Map<String, Boolean> transitiveOverride) {
        context.checking(new Expectations() {{
            allowing(moduleDescriptorConverterMock).convert(transitiveOverride, configurationContainer, moduleDescriptorConfigurationFilter, dependencyContainerMock, moduleDescriptorDependencyFilter,
                    moduleConverterArtifactContainer, moduleDescriptorArtifactFilter);
            will(returnValue(testModuleDescriptor));

            allowing(settingsConverterMock).convert(settingsConverterDependencyResolvers, settingsConverterPublishResolvers, testGradleUserHome,
                    testBuildResolver, settingsConverterClientModuleRegistry);
            will(returnValue(testIvySettings));
        }});
    }
}
