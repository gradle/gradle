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
package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.model.Exclusion;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.publication.maven.internal.ExcludeRuleConverter;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultPomDependenciesConverterTest {
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    
    private DefaultPomDependenciesConverter dependenciesConverter;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
    private ExcludeRuleConverter excludeRuleConverterMock = context.mock(ExcludeRuleConverter.class);

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
        return createNamedConfigurationStubWithDependencies(confName, new HashSet<ExcludeRule>(), dependencies);
    }
    
    private Configuration createNamedConfigurationStubWithDependencies(final String confName, final Set<ExcludeRule> excludeRules, final ModuleDependency... dependencies) {
        final Configuration configurationStub = context.mock(Configuration.class, confName);
        final DependencySet dependencySet = context.mock(DependencySet.class);

        context.checking(new Expectations() {{
            allowing(configurationStub).getName();
            will(returnValue(confName));
            allowing(configurationStub).getDependencies();
            will(returnValue(dependencySet));
            allowing(dependencySet).withType(ModuleDependency.class);
            will(returnValue(toDomainObjectSet(ModuleDependency.class, dependencies)));
            allowing(configurationStub).getExcludeRules();
            will(returnValue(excludeRules));
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
        List<org.apache.maven.model.Dependency> actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, configurations);
        assertEquals(4, actualMavenDependencies.size());
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
        List<org.apache.maven.model.Dependency> actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(
                compileConfStub, testCompileConfStub, unmappedConfigurationStub));
        assertEquals(4, actualMavenDependencies.size());
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
        List<org.apache.maven.model.Dependency> actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(
                compileConfStub, testCompileConfStub, unmappedConfigurationStub));
        assertEquals(5, actualMavenDependencies.size());
        checkCommonMavenDependencies(actualMavenDependencies);
        assertTrue(hasDependency(actualMavenDependencies, "org4", "name4", "rev4", null, null, null, false));
    }

    private void checkCommonMavenDependencies(List<org.apache.maven.model.Dependency> actualMavenDependencies) {
        assertTrue(hasDependency(actualMavenDependencies, "org1", "name1", "rev1", null, "compile", null, false));
        assertTrue(hasDependency(actualMavenDependencies, "org2", "name2", "rev2", null, "test", null, false));
        assertTrue(hasDependency(actualMavenDependencies, "org3", "name3", "rev3", null, "test", null, false));
        assertTrue(hasDependency(actualMavenDependencies, "org3", "artifactName32", "rev3", "type32", "test", "classifier32", false));
    }

    private boolean hasDependency(List<org.apache.maven.model.Dependency> mavenDependencies,
                                  String group, String artifactId, String version, String type, String scope,
                                  String classifier, boolean optional) {
        org.apache.maven.model.Dependency expectedDependency = new org.apache.maven.model.Dependency();
        expectedDependency.setGroupId(group);
        expectedDependency.setArtifactId(artifactId);
        expectedDependency.setVersion(version);
        expectedDependency.setType(type);
        expectedDependency.setScope(scope);
        expectedDependency.setClassifier(classifier);
        expectedDependency.setOptional(optional);
        for (org.apache.maven.model.Dependency mavenDependency : mavenDependencies) {
            if (equals(mavenDependency, expectedDependency)) {
                return true;
            }
        }
        return false;
    }

    private boolean equals(org.apache.maven.model.Dependency lhs, org.apache.maven.model.Dependency rhs) {
        if (!lhs.getGroupId().equals(lhs.getGroupId())) {
            return false;
        }
        if (!lhs.getArtifactId().equals(lhs.getArtifactId())) {
            return false;
        }
        if (!lhs.getVersion().equals(lhs.getVersion())) {
            return false;
        }
        if (lhs.getType() != null ? !lhs.getType().equals(lhs.getType()) : rhs.getType() != null) {
            return false;
        }
        if (lhs.getScope() != null ? !lhs.getScope().equals(lhs.getScope()) : rhs.getScope() != null) {
            return false;
        }
        if (!lhs.isOptional() == lhs.isOptional()) {
            return false;
        }
        if (lhs.getClassifier() != null ? !lhs.getClassifier().equals(rhs.getClassifier()) : rhs.getClassifier() != null) {
            return false;
        }
        return true;
    }

    @Test
    public void convertWithConvertableDependencyExcludes() {
        final Configuration someConfigurationStub = createNamedConfigurationStubWithDependencies("someConfiguration", dependency1);
        final Exclusion mavenExclude = new Exclusion();
        mavenExclude.setGroupId("a");
        mavenExclude.setArtifactId("b");
        dependency1.exclude(toMap(ExcludeRule.GROUP_KEY, "value"));
        context.checking(new Expectations() {{
           allowing(conf2ScopeMappingContainerMock).getMapping(toSet(someConfigurationStub)); will(returnValue(createMapping(compileConfStub, "compile")));
           allowing(excludeRuleConverterMock).convert(dependency1.getExcludeRules().iterator().next()); will(returnValue(mavenExclude));
        }});
        List<org.apache.maven.model.Dependency> actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(someConfigurationStub));
        assertEquals(1, actualMavenDependencies.size());
        assertTrue(hasDependency(actualMavenDependencies, "org1", "name1", "rev1", null, "compile", null, false));
        org.apache.maven.model.Dependency mavenDependency = (org.apache.maven.model.Dependency) actualMavenDependencies.get(0);
        assertThat(mavenDependency.getExclusions().size(), equalTo(1));
        assertThat(((Exclusion) mavenDependency.getExclusions().get(0)).getGroupId(), equalTo(mavenExclude.getGroupId()));
        assertThat(((Exclusion) mavenDependency.getExclusions().get(0)).getArtifactId(), equalTo(mavenExclude.getArtifactId()));
    }
    
    @Test
    public void convertWithConvertableConfigurationExcludes() {
        final Configuration someConfigurationStub = createNamedConfigurationStubWithDependencies("someConfiguration", 
                WrapUtil.<ExcludeRule>toSet(new DefaultExcludeRule("value", null)), dependency1);
        final Exclusion mavenExclude = new Exclusion();
        mavenExclude.setGroupId("a");
        mavenExclude.setArtifactId("b");
        context.checking(new Expectations() {{
           allowing(conf2ScopeMappingContainerMock).getMapping(toSet(someConfigurationStub)); will(returnValue(createMapping(compileConfStub, "compile")));
           allowing(excludeRuleConverterMock).convert(someConfigurationStub.getExcludeRules().iterator().next()); will(returnValue(mavenExclude));
        }});
        List<org.apache.maven.model.Dependency> actualMavenDependencies = dependenciesConverter.convert(conf2ScopeMappingContainerMock, toSet(someConfigurationStub));
        assertEquals(1, actualMavenDependencies.size());
        assertTrue(hasDependency(actualMavenDependencies, "org1", "name1", "rev1", null, "compile", null, false));
        org.apache.maven.model.Dependency mavenDependency = (org.apache.maven.model.Dependency) actualMavenDependencies.get(0);
        assertThat(mavenDependency.getExclusions().size(), equalTo(1));
        assertThat(((Exclusion) mavenDependency.getExclusions().get(0)).getGroupId(), equalTo(mavenExclude.getGroupId()));
        assertThat(((Exclusion) mavenDependency.getExclusions().get(0)).getArtifactId(), equalTo(mavenExclude.getArtifactId()));
    }
}
