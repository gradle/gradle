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
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ConfigurationResolveReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultResolutionStrategy;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

import static java.util.Collections.emptySet;
import static org.gradle.api.artifacts.ArtifactsTestUtils.createResolvedArtifact;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyDependencyResolverTest {
    private JUnit4Mockery context = new JUnit4GroovyMockery();

    private ConfigurationInternal configurationStub = context.mock(ConfigurationInternal.class, "<configuration>");
    private ResolutionStrategyInternal resolutionStrategy = context.mock(ResolutionStrategyInternal.class);
    private Ivy ivyStub = context.mock(Ivy.class);
    private DefaultIvyReportConverter ivyReportConverterStub = context.mock(DefaultIvyReportConverter.class);
    private ResolveReport resolveReportMock = context.mock(ResolveReport.class);
    private ModuleDescriptorConverter moduleDescriptorConverter = context.mock(ModuleDescriptorConverter.class);
    private Configuration otherConfiguration = context.mock(Configuration.class);
    private Module module = context.mock(Module.class);
    private ResolveIvyFactory ivyFactory = context.mock(ResolveIvyFactory.class);
    private IvyConversionResult conversionResultStub = context.mock(IvyConversionResult.class);

    private DefaultIvyDependencyResolver ivyDependencyResolver = new DefaultIvyDependencyResolver(ivyReportConverterStub, moduleDescriptorConverter, ivyFactory);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(configurationStub).getName();
            will(returnValue("someConfName"));
            allowing(ivyStub).getSettings();
            will(returnValue(new IvySettings()));
            allowing(configurationStub).getResolutionStrategy();
            will(returnValue(resolutionStrategy));
        }});

        configurationIsAskedForConflictStrategy();
    }

    @Test
    public void testResolveAndGetFilesWithDependencySubset() throws IOException, ParseException {
        prepareResolveReport();
        final ModuleDependency moduleDependencyDummy1 = context.mock(ModuleDependency.class, "dep1");
        final ModuleDependency moduleDependencyDummy2 = context.mock(ModuleDependency.class, "dep2");
        final SelfResolvingDependency selfResolvingDependencyDummy = context.mock(SelfResolvingDependency.class);
        final ResolvedDependency root = context.mock(ResolvedDependency.class, "root");
        final ResolvedDependency resolvedDependency1 = context.mock(ResolvedDependency.class, "resolved1");
        final ResolvedDependency resolvedDependency2 = context.mock(ResolvedDependency.class, "resolved2");
        ResolvedDependency resolvedDependency3 = context.mock(ResolvedDependency.class, "resolved3");
        final DependencySet dependencies = context.mock(DependencySet.class);
        final Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies = new LinkedHashMap<Dependency, Set<ResolvedDependency>>();
        firstLevelResolvedDependencies.put(moduleDependencyDummy1, toSet(resolvedDependency1, resolvedDependency2));
        firstLevelResolvedDependencies.put(moduleDependencyDummy2, toSet(resolvedDependency3));

        context.checking(new Expectations() {{
            allowing(resolvedDependency1).getParentArtifacts(root);
            will(returnValue(toSet(createResolvedArtifact(context, "dep1parent", "someType", "someExtension", new File("dep1parent")))));
            allowing(resolvedDependency1).getChildren();
            will(returnValue(emptySet()));
            allowing(resolvedDependency2).getParentArtifacts(root);
            will(returnValue(toSet(createResolvedArtifact(context, "dep2parent", "someType", "someExtension", new File("dep2parent")))));
            allowing(resolvedDependency2).getChildren();
            will(returnValue(emptySet()));
            allowing(configurationStub).getAllDependencies();
            will(returnValue(dependencies));
            allowing(dependencies).withType(ModuleDependency.class);
            will(returnValue(toDomainObjectSet(ModuleDependency.class, moduleDependencyDummy1, moduleDependencyDummy2)));
            allowing(conversionResultStub).getFirstLevelResolvedDependencies();
            will(returnValue(firstLevelResolvedDependencies));
            allowing(conversionResultStub).getRoot();
            will(returnValue(root));
        }});

        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);

        ResolvedConfiguration resolvedConfig = ivyDependencyResolver.resolve(configurationStub);
        Set<File> actualFiles = resolvedConfig.getFiles(
                new Spec<Dependency>() {
                    public boolean isSatisfiedBy(Dependency element) {
                        return element == moduleDependencyDummy1 || element == selfResolvingDependencyDummy;
                    }
                });
        assertThat(actualFiles, equalTo(toSet(new File("dep1parent"), new File("dep2parent"))));

        Set<ResolvedDependency> actualDeps = resolvedConfig.getFirstLevelModuleDependencies(
                new Spec<Dependency>() {
                    public boolean isSatisfiedBy(Dependency element) {
                        return element == moduleDependencyDummy1;
                    }
                });
        assertThat(actualDeps, equalTo(toSet(resolvedDependency1, resolvedDependency2)));
    }

    private void configurationIsAskedForConflictStrategy() {
        context.checking(new Expectations() {{
            allowing(configurationStub).getResolutionStrategy();
            will(returnValue(new DefaultResolutionStrategy()));
        }});
    }

    @Test
    public void testGetModuleDependencies() throws IOException, ParseException {
        prepareResolveReport();
        final ResolvedDependency root = context.mock(ResolvedDependency.class, "root");
        final ResolvedDependency resolvedDependency1 = context.mock(ResolvedDependency.class, "resolved1");
        final ResolvedDependency resolvedDependency2 = context.mock(ResolvedDependency.class, "resolved2");
        final Set<ResolvedDependency> resolvedDependenciesSet = toSet(resolvedDependency1, resolvedDependency2);

        context.checking(new Expectations() {{
            allowing(conversionResultStub).getRoot();
            will(returnValue(root));
            allowing(root).getChildren();
            will(returnValue(resolvedDependenciesSet));
        }});

        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);

        Set<ResolvedDependency> actualFirstLevelModuleDependencies = ivyDependencyResolver.resolve(configurationStub).getFirstLevelModuleDependencies();
        assertThat(actualFirstLevelModuleDependencies, equalTo(resolvedDependenciesSet));
    }

    @Test
    public void testGetResolvedArtifacts() throws IOException, ParseException {
        prepareResolveReport();
        final ResolvedArtifact resolvedArtifactDummy = context.mock(ResolvedArtifact.class);
        final Set<ResolvedArtifact> resolvedArtifacts = toSet(resolvedArtifactDummy);
        context.checking(new Expectations() {{
            allowing(conversionResultStub).getResolvedArtifacts();
            will(returnValue(resolvedArtifacts));
        }});
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        assertThat(ivyDependencyResolver.resolve(configurationStub)
                .getResolvedArtifacts(), equalTo(resolvedArtifacts));
    }

    @Test
    public void testResolveAndGetFilesWithMissingDependenciesShouldThrowResolveException() throws IOException, ParseException {
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        Exception failure = new Exception("broken");
        prepareResolveReportWithError(failure);
        ResolvedConfiguration configuration = ivyDependencyResolver.resolve(configurationStub);
        context.checking(new Expectations() {{
            allowing(configurationStub).getAllDependencies();
        }});
        try {
            configuration.getFiles(Specs.SATISFIES_ALL);
            fail();
        } catch (ResolveException e) {
            assertThat(e.getMessage(), startsWith("Could not resolve all dependencies for <configuration>"));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }
    }

    @Test
    public void testResolveAndRethrowFailureWithMissingDependenciesShouldThrowResolveException() throws IOException, ParseException {
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        Exception failure = new Exception("broken");
        prepareResolveReportWithError(failure);
        ResolvedConfiguration configuration = ivyDependencyResolver.resolve(configurationStub);

        assertTrue(configuration.hasError());
        try {
            configuration.rethrowFailure();
            fail();
        } catch (ResolveException e) {
            assertThat(e.getMessage(), startsWith("Could not resolve all dependencies for <configuration>"));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }
    }

    @Test
    public void testResolveAndRethrowFailureWithNoMissingDependenciesShouldDoNothing() throws IOException, ParseException {
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        prepareResolveReport();
        ResolvedConfiguration configuration = ivyDependencyResolver.resolve(configurationStub);

        assertFalse(configuration.hasError());
        configuration.rethrowFailure();
    }

    @Test
    public void testResolveAndGetReport() throws IOException, ParseException {
        prepareResolveReport();
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        assertEquals(false, ivyDependencyResolver.resolve(configurationStub).hasError());
    }

    @Test
    public void testResolveAndGetReportWithMissingDependenciesAndFailFalse() throws IOException, ParseException {
        prepareResolveReportWithError(new Exception("broken"));
        ModuleDescriptor moduleDescriptor = createAnonymousModuleDescriptor();
        prepareTestsThatRetrieveDependencies(moduleDescriptor);
        assertEquals(true, ivyDependencyResolver.resolve(configurationStub).hasError());
    }

    private ModuleDescriptor createAnonymousModuleDescriptor() {
        return DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance("org", "name", "1.0", new HashMap()));
    }

    private void prepareResolveReport() throws IOException, ParseException {
        context.checking(new Expectations() {
            {
                allowing(resolveReportMock).hasError();
                will(returnValue(false));
            }
        });
    }

    private void prepareResolveReportWithError(final Exception failure) throws IOException, ParseException {
        context.checking(new Expectations() {{
            ConfigurationResolveReport configurationResolveReport = context.mock(ConfigurationResolveReport.class);

            allowing(resolveReportMock).hasError();
            will(returnValue(true));
            allowing(resolveReportMock).getAllProblemMessages();
            will(returnValue(toList("a problem")));
            allowing(resolveReportMock).getConfigurationReport("someConfName");
            will(returnValue(configurationResolveReport));
            allowing(configurationResolveReport).getUnresolvedDependencies();
            IvyNode unresolvedNode = context.mock(IvyNode.class);
            will(returnValue(new IvyNode[]{unresolvedNode}));
            allowing(unresolvedNode).getProblem();
            will(returnValue(failure));
        }});
    }

    private void prepareTestsThatRetrieveDependencies(final ModuleDescriptor moduleDescriptor) throws IOException, ParseException {
        final String confName = configurationStub.getName();
        context.checking(new Expectations() {{
            allowing(configurationStub).getAll();
            will(returnValue(toSet(configurationStub, otherConfiguration)));
            allowing(configurationStub).getModule();
            will(returnValue(module));
            one(ivyFactory).create(resolutionStrategy);
            will(returnValue(ivyStub));
            one(moduleDescriptorConverter)
                    .convert(with(equalTo(toSet(configurationStub, otherConfiguration))), with(equalTo(module)));
            will(returnValue(moduleDescriptor));
            one(ivyStub).resolve(with(equal(moduleDescriptor)), with(equalResolveOptions(confName)));
            will(returnValue(resolveReportMock));
            one(ivyReportConverterStub).convertReport(resolveReportMock, configurationStub);
            will(returnValue(conversionResultStub));
        }});
    }

    Matcher<ResolveOptions> equalResolveOptions(final String... confs) {
        return new BaseMatcher<ResolveOptions>() {
            public boolean matches(Object o) {
                ResolveOptions otherOptions = (ResolveOptions) o;
                return Arrays.equals(confs, otherOptions.getConfs());
            }

            public void describeTo(Description description) {
                description.appendText("Checking Resolveoptions");
            }
        };
    }

}
