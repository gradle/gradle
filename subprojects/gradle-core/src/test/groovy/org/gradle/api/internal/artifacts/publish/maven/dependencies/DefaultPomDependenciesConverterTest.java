/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish.maven.dependencies;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.*;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultPomDependenciesConverterTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    
    private DefaultPomDependenciesConverter dependenciesConverter;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
    private ExcludeRuleConverter excludeRuleConverterMock = context.mock(ExcludeRuleConverter.class);

    private MavenPom pomMock = context.mock(MavenPom.class);
    private ModuleDependency dependency1;
    private ModuleDependency dependency2;
    private ModuleDependency dependency31;
    private ModuleDependency dependency32;
    private Configuration compileConfStub;
    private Configuration testCompileConfStub;

    @Before
    public void setUp() {
        setUpCommonDependenciesAndConfigurations();
        dependenciesConverter = new DefaultPomDependenciesConverter(excludeRuleConverterMock);
        context.checking(new Expectations() {{
            allowing(pomMock).getScopeMappings();
            will(returnValue(conf2ScopeMappingContainerMock));
        }});
    }

    private void setUpCommonDependenciesAndConfigurations() {
        dependency1 = createDependency("org1", "name1", "rev1");
        dependency2 = createDependency("org2", "name2", "rev2");
        dependency2.addArtifact(new DefaultDependencyArtifact("name2", null, null, null, null));
        dependency31 = createDependency("org3", "name3", "rev3");
        dependency32 = createDependency("org3", "name3", "rev3");
        dependency32.addArtifact(new DefaultDependencyArtifact("artifactName32", "type32", "ext", "classifier32", null));
        compileConfStub = createNamedConfigurationStubWithDependencies("compile", dependency1, dependency31);
        testCompileConfStub = createNamedConfigurationStubWithDependencies("testCompile", dependency2, dependency32);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).getMapping(toSet(testCompileConfStub, compileConfStub)); will(returnValue(createMapping(testCompileConfStub, "test")));
            allowing(conf2ScopeMappingContainerMock).getMapping(toSet(compileConfStub, testCompileConfStub)); will(returnValue(createMapping(testCompileConfStub, "test")));
            allowing(conf2ScopeMappingContainerMock).getMapping(toSet(testCompileConfStub)); will(returnValue(createMapping(testCompileConfStub, "test")));
            allowing(conf2ScopeMappingContainerMock).getMapping(toSet(compileConfStub)); will(returnValue(createMapping(compileConfStub, "compile")));
        }});
    }

    private Conf2ScopeMapping createMapping(Configuration configuration, String scope) {
        return new Conf2ScopeMapping(10, configuration, scope);
    }

    private Configuration createNamedConfigurationStubWithDependencies(final String confName, final ModuleDependency... dependencies) {
        final Configuration configurationStub = context.mock(Configuration.class, confName);
        context.checking(new Expectations() {{
            allowing(configurationStub).getName();
            will(returnValue(confName));
            allowing(configurationStub).getDependencies(ModuleDependency.class);
            will(returnValue(toSet(dependencies)));
        }});
        return configurationStub;
    }

    private ModuleDependency createDependency(final String group, final String name, final String version) {
        return new DefaultExternalModuleDependency(group, name, version);
    }

    @Test
    public void init() {
        assertSame(excludeRuleConverterMock, dependenciesConverter.getExcludeRuleConverter());
    }

    @Test
    public void convert() {
        Set<Configuration> configurations = toSet(compileConfStub, testCompileConfStub);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, configurations);
        System.out.println("actualMavenDependencies = " + actualMavenDependencies);
        assertEquals(3, actualMavenDependencies.size());
        checkCommonMavenDependencies(actualMavenDependencies);
    }

    @Test
    public void convertWithUnMappedConfAndSkipTrue() {
        final Dependency dependency4 = createDependency("org4", "name4", "rev4");
        final Configuration unmappedConfigurationStub = createNamedConfigurationStubWithDependencies("unmappedConf");
        context.checking(new Expectations() {{
            allowing(unmappedConfigurationStub).getDependencies();
            will(returnValue(toSet(dependency4)));
        }});
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(true));
            allowing(conf2ScopeMappingContainerMock).getMapping(asList(unmappedConfigurationStub)); will(returnValue(null));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, toSet(
                compileConfStub, testCompileConfStub, unmappedConfigurationStub));
        assertEquals(3, actualMavenDependencies.size());
        checkCommonMavenDependencies(actualMavenDependencies);
    }

    @Test
    public void convertWithUnMappedConfAndSkipFalse() {
        final ModuleDependency dependency4 = createDependency("org4", "name4", "rev4");
        final Configuration unmappedConfigurationStub = createNamedConfigurationStubWithDependencies("unmappedConf", dependency4);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
            allowing(conf2ScopeMappingContainerMock).getMapping(toSet(unmappedConfigurationStub)); will(returnValue(new Conf2ScopeMapping(null, unmappedConfigurationStub, null)));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, toSet(
                compileConfStub, testCompileConfStub, unmappedConfigurationStub));
        assertEquals(4, actualMavenDependencies.size());
        checkCommonMavenDependencies(actualMavenDependencies);
        assertThat(actualMavenDependencies,
                hasItem((MavenDependency) DefaultMavenDependency.newInstance("org4", "name4", "rev4", null, null)));
    }

    private void checkCommonMavenDependencies(List<MavenDependency> actualMavenDependencies) {
        assertThat(actualMavenDependencies,
                hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        assertThat(actualMavenDependencies,
                hasItem((MavenDependency) DefaultMavenDependency.newInstance("org2", "name2", "rev2", null, "test")));
        assertThat(actualMavenDependencies,
                hasItem((MavenDependency) new DefaultMavenDependency("org3", "artifactName32", "rev3", "type32", "test",
                        Collections.<MavenExclude>emptyList(), false, "classifier32")));
    }

    @Test
    public void convertWithConvertableExcludes() {
        final Configuration someConfigurationStub = createNamedConfigurationStubWithDependencies("someConfiguration", dependency1);
        final DefaultMavenExclude mavenExclude = new DefaultMavenExclude("a", "b");
        dependency1.exclude(toMap("key", "value"));
        context.checking(new Expectations() {{
           allowing(conf2ScopeMappingContainerMock).getMapping(toSet(someConfigurationStub)); will(returnValue(createMapping(compileConfStub, "compile")));
           allowing(excludeRuleConverterMock).convert(dependency1.getExcludeRules().iterator().next()); will(returnValue(mavenExclude));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, toSet(someConfigurationStub));
        assertEquals(1, actualMavenDependencies.size());
        assertThat(actualMavenDependencies,
                hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        assertEquals(actualMavenDependencies.get(0).getMavenExcludes(), toList(mavenExclude));
        assertEquals(actualMavenDependencies.get(0).getScope(), "compile");

    }
}
