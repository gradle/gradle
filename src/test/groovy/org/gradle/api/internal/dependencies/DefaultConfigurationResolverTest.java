/*
 * Copyright 2007, 2008 the original author or authors.
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
package org.gradle.api.internal.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.Transformer;
import org.gradle.api.dependencies.*;
import org.gradle.api.internal.dependencies.ivy.IvyService;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
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
import java.util.Set;

@RunWith (JMock.class)
public class DefaultConfigurationResolverTest {
    private static final File TEST_GRADLE_USER_HOME = new File("testGradleUserHome");
    private static final ResolveInstruction TEST_RESOLVE_INSTRUCTION = new ResolveInstruction();
    private static final List<File> TEST_RESOLVE_RESULT = WrapUtil.toList(new File("someFile"));
    private static final String TEST_CONF_NAME = "testConf";
    
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    
    private final Configuration configurationMock = context.mock(Configuration.class);
    private final DependencyContainerInternal dependencyContainerMock = context.mock(DependencyContainerInternal.class);
    private final ArtifactContainer artifactContainerMock = context.mock(ArtifactContainer.class);


    private final ConfigurationContainer publishConfigurationContainerMock = context.mock(ConfigurationContainer.class);
    private final ResolverContainer dependencyResolversMock = context.mock(ResolverContainer.class);
    private final IvyService ivyServiceMock = context.mock(IvyService.class);
    private final DefaultConfigurationResolver configurationResolver = new DefaultConfigurationResolver(configurationMock,
            dependencyContainerMock, artifactContainerMock, publishConfigurationContainerMock, dependencyResolversMock,
            ivyServiceMock, TEST_GRADLE_USER_HOME);
    private List<DependencyResolver> testDependencyResolvers = WrapUtil.toList(context.mock(DependencyResolver.class));
    private Set<ConfigurationResolver> chainConfigurations = WrapUtil.<ConfigurationResolver>toSet(configurationResolver);
    private ResolveReport testResolveReport = context.mock(ResolveReport.class);
    private Set<Configuration> dependencyContainerConfigurations = WrapUtil.toSet(context.mock(Configuration.class, "dependencyContainerConfMock"));

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(configurationMock).getResolveInstruction();
            will(returnValue(TEST_RESOLVE_INSTRUCTION));

            allowing(configurationMock).getName();
            will(returnValue(TEST_CONF_NAME));

            allowing(dependencyResolversMock).getResolverList();
            will(returnValue(testDependencyResolvers));

            allowing(configurationMock).getChain();
            will(returnValue(WrapUtil.toSet(configurationMock)));

            allowing(dependencyContainerMock).getConfigurations();
            will(returnValue(dependencyContainerConfigurations));
        }});
    }
    
    @Test
    public void init() {
        assertThat(configurationResolver.getWrappedConfiguration(), sameInstance(configurationMock));
        assertThat(configurationResolver.getArtifactContainer(), sameInstance(artifactContainerMock));
        assertThat(configurationResolver.getDependencyContainer(), sameInstance(dependencyContainerMock));
        assertThat(configurationResolver.getDependencyResolvers(), sameInstance(dependencyResolversMock));
        assertThat(configurationResolver.getGradleUserHome(), sameInstance(TEST_GRADLE_USER_HOME));
        assertThat(configurationResolver.getIvyHandler(), sameInstance(ivyServiceMock));
        assertThat(configurationResolver.getPublishConfigurationContainer(), sameInstance(publishConfigurationContainerMock));
    }

    @Test
    public void withPrivateVisibility() {
        context.checking(new Expectations() {{
            one(configurationMock).setVisible(false);
        }});
        assertThat((DefaultConfigurationResolver) configurationResolver.setVisible(false), sameInstance(configurationResolver));
    }

    @Test
    public void withIntransitive() {
        context.checking(new Expectations() {{
            one(configurationMock).setTransitive(false);
        }});
        assertThat((DefaultConfigurationResolver) configurationResolver.setTransitive(false), sameInstance(configurationResolver));
    }

    @Test
    public void withDescription() {
        final String testDescription = "testDescription";
        context.checking(new Expectations() {{
            one(configurationMock).setDescription(testDescription);
        }});
        assertThat((DefaultConfigurationResolver) configurationResolver.setDescription(testDescription), sameInstance(configurationResolver));

    }
    
    @Test
    public void extendsFrom() {
        final String[] testConfs = WrapUtil.toArray("conf1", "conf2");
        context.checking(new Expectations() {{
            one(configurationMock).extendsFrom(testConfs);
        }});
        assertThat((DefaultConfigurationResolver) configurationResolver.extendsFrom(testConfs), sameInstance(configurationResolver));
    }
    
    @Test
    public void transformsIvyConfigurationObject() {
        final Transformer<org.apache.ivy.core.module.descriptor.Configuration> testTransformer =
                context.mock(Transformer.class);
        context.checking(new Expectations() {{
            one(configurationMock).addIvyTransformer(testTransformer);
        }});
        configurationResolver.addIvyTransformer(testTransformer);

    }

    @Test
    public void transformsIvyConfigurationObjectUsingClosure() {
        final Closure testClosure = HelperUtil.TEST_CLOSURE;
        context.checking(new Expectations() {{
            one(configurationMock).addIvyTransformer(testClosure);
        }});
        configurationResolver.addIvyTransformer(testClosure);

    }

    @Test
    public void getResolveInstruction() {
        assertThat(configurationResolver.getResolveInstruction(), sameInstance(TEST_RESOLVE_INSTRUCTION));
    }
    
    @Test
    public void getIvyConfiguration() {
        final org.apache.ivy.core.module.descriptor.Configuration testConfiguration = new org.apache.ivy.core.module.descriptor.Configuration("name");
        context.checking(new Expectations() {{
            allowing(configurationMock).getIvyConfiguration(true);
            will(returnValue(testConfiguration));
        }});
        assertThat(configurationResolver.getIvyConfiguration(true), sameInstance(testConfiguration));
    }

    @Test
    public void chain() {
        assertThat(configurationResolver.getChain(), equalTo(chainConfigurations));
    }

    @Test
    public void getExtendsFrom() {
        final Configuration superConf = context.mock(Configuration.class, "superConf");
        context.checking(new Expectations() {{
            allowing(configurationMock).getExtendsFrom();
            will(returnValue(WrapUtil.toSet(superConf)));
        }});
        assertThat(configurationResolver.getExtendsFrom().size(), equalTo(1));
        DefaultConfigurationResolver superConfigurationResolver = (DefaultConfigurationResolver) configurationResolver.getExtendsFrom().iterator().next();
        assertThat(superConf, sameInstance(superConfigurationResolver.getWrappedConfiguration()));
    }

    @Test
    public void resolve() {
        context.checking(new Expectations() {{
            allowing(ivyServiceMock).resolve(TEST_CONF_NAME, dependencyContainerConfigurations, dependencyContainerMock,
                    testDependencyResolvers, TEST_RESOLVE_INSTRUCTION, TEST_GRADLE_USER_HOME);
            will(returnValue(TEST_RESOLVE_RESULT));
        }});
        assertThat(configurationResolver.resolve(), equalTo(TEST_RESOLVE_RESULT));
    }

    @Test
    public void resolveWithInstructionModifier() {
        final ResolveInstruction expectedResolveInstruction = new ResolveInstruction();
        expectedResolveInstruction.setDependencyFilter(HelperUtil.TEST_SEPC);
        context.checking(new Expectations() {{
            allowing(ivyServiceMock).resolve(TEST_CONF_NAME, dependencyContainerConfigurations, dependencyContainerMock,
                    testDependencyResolvers, expectedResolveInstruction, TEST_GRADLE_USER_HOME);
            will(returnValue(TEST_RESOLVE_RESULT));
        }});
        ResolveInstructionModifier resolveInstructionModifier = new ResolveInstructionModifier() {
            public ResolveInstruction modify(ResolveInstruction resolveInstruction) {
                ResolveInstruction newResolveInstruction = new ResolveInstruction(resolveInstruction);
                newResolveInstruction.setDependencyFilter(HelperUtil.TEST_SEPC);
                return newResolveInstruction;
            }
        };
        assertThat(configurationResolver.resolve(resolveInstructionModifier), equalTo(TEST_RESOLVE_RESULT));
    }

    @Test
    public void resolveAsReportWithInstructionModifier() {
        final ResolveInstruction expectedResolveInstruction = new ResolveInstruction();
        expectedResolveInstruction.setDependencyFilter(HelperUtil.TEST_SEPC);
        context.checking(new Expectations() {{
            allowing(ivyServiceMock).resolveAsReport(TEST_CONF_NAME, dependencyContainerConfigurations, dependencyContainerMock,
                    testDependencyResolvers, expectedResolveInstruction, TEST_GRADLE_USER_HOME);
            will(returnValue(testResolveReport));
        }});
        ResolveInstructionModifier resolveInstructionModifier = new ResolveInstructionModifier() {
            public ResolveInstruction modify(ResolveInstruction resolveInstruction) {
                ResolveInstruction newResolveInstruction = new ResolveInstruction(resolveInstruction);
                newResolveInstruction.setDependencyFilter(HelperUtil.TEST_SEPC);
                return newResolveInstruction;
            }
        };
        assertThat(configurationResolver.resolveAsReport(resolveInstructionModifier), equalTo(testResolveReport));
    }

    @Test
    public void uploadInternalTaskName() {
        assertThat(configurationResolver.getUploadInternalTaskName(), equalTo("uploadTestConfInternal"));
    }

    @Test
    public void uploadTaskName() {
        assertThat(configurationResolver.getUploadTaskName(), equalTo("uploadTestConf"));
    }
}