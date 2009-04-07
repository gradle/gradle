/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultModuleDependency;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

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
    private Dependency dependency1;
    private Dependency dependency2;
    private Dependency dependency31;
    private Dependency dependency32;
    private Configuration configurationCompileConfStub;
    private Configuration configurationTestCompileConfStub;
    private static final String COMPILE_CONF_NAME = "compile";
    private static final String TEST_COMPILE_CONF_NAME = "testCompile";

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
        dependency31 = createDependency("org3", "name3", "rev3");
        dependency32 = createDependency("org3", "name3", "rev3");
        dependency31.addArtifact(new DefaultDependencyArtifact("artifactName31", "type31", "ext", null, null));
        dependency32.addArtifact(new DefaultDependencyArtifact("artifactName32", "type32", "ext", null, null));
        configurationCompileConfStub = createNamedConfigurationStubWithDependencies(COMPILE_CONF_NAME, dependency1, dependency31);
        configurationTestCompileConfStub = createNamedConfigurationStubWithDependencies(TEST_COMPILE_CONF_NAME, dependency2, dependency32);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).getScope(TEST_COMPILE_CONF_NAME, COMPILE_CONF_NAME); will(returnValue("test"));
            allowing(conf2ScopeMappingContainerMock).getScope(COMPILE_CONF_NAME, TEST_COMPILE_CONF_NAME); will(returnValue("test"));
            allowing(conf2ScopeMappingContainerMock).getScope(TEST_COMPILE_CONF_NAME); will(returnValue("test"));
            allowing(conf2ScopeMappingContainerMock).getScope(COMPILE_CONF_NAME); will(returnValue("compile"));
        }});
    }

    private Configuration createNamedConfigurationStubWithDependencies(final String confName, final Dependency... dependencies) {
        final Configuration configurationStub = context.mock(Configuration.class, confName);
        context.checking(new Expectations() {{
            allowing(configurationStub).getName();
            will(returnValue(confName));
            allowing(configurationStub).getDependencies();
            will(returnValue(WrapUtil.toSet(dependencies)));
        }});
        return configurationStub;
    }

    private Dependency createDependency(final String group, final String name, final String version) {
        return new DefaultModuleDependency(group, name, version);
    }

    @Test
    public void init() {
        assertSame(excludeRuleConverterMock, dependenciesConverter.getExcludeRuleConverter());
    }

    @Ignore
    public void convert() {
        Set<Configuration> configurations = WrapUtil.toSet(configurationCompileConfStub, configurationTestCompileConfStub);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, configurations);
        assertEquals(4, actualMavenDependencies.size());
        checkCommonMavenDependencies(actualMavenDependencies);
    }

    @Ignore
    public void convertWithUnMappedConfAndSkipTrue() {
        final Dependency dependency4 = createDependency("org4", "name4", "rev4");
        final String unmappedConfName = "unmappedConf";
        final Configuration unmappedConfigurationStub = createNamedConfigurationStubWithDependencies(unmappedConfName);
        context.checking(new Expectations() {{
            allowing(unmappedConfigurationStub).getDependencies();
            will(returnValue(WrapUtil.toSet(dependency4)));
        }});
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(true));
            allowing(conf2ScopeMappingContainerMock).getScope(unmappedConfName); will(returnValue(null));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, WrapUtil.toSet(
                configurationCompileConfStub, configurationTestCompileConfStub, unmappedConfigurationStub));
        assertEquals(4, actualMavenDependencies.size());
        checkCommonMavenDependencies(actualMavenDependencies);
    }

    @Ignore
    public void convertWithUnMappedConfAndSkipFalse() {
        final Dependency dependency4 = createDependency("org4", "name4", "rev4");
        final String unmappedConfName = "unmappedConf";
        final Configuration unmappedConfigurationStub = createNamedConfigurationStubWithDependencies(unmappedConfName, dependency4);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
            allowing(conf2ScopeMappingContainerMock).getScope(unmappedConfName); will(returnValue(null));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, WrapUtil.toSet(
                configurationCompileConfStub, configurationTestCompileConfStub, unmappedConfigurationStub));
        assertEquals(5, actualMavenDependencies.size());
        checkCommonMavenDependencies(actualMavenDependencies);
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org4", "name4", "rev4", null, null)));
    }

    private void checkCommonMavenDependencies(List<MavenDependency> actualMavenDependencies) {
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org2", "name2", "rev2", null, "test")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org3", "artifactName31", "rev3", "type31", "compile")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org3", "artifactName32", "rev3", "type32", "test")));
    }

    @Ignore
    public void convertWithConvertableExcludes() {
        final String someConfName = "someConfiguration";
        final Configuration someConfigurationStub = createNamedConfigurationStubWithDependencies(someConfName, dependency1);
        final DefaultMavenExclude mavenExclude = new DefaultMavenExclude("a", "b");
        dependency1.exclude(WrapUtil.toMap("key", "value"));
        context.checking(new Expectations() {{
           allowing(conf2ScopeMappingContainerMock).getScope(someConfName); will(returnValue("compile"));
           allowing(excludeRuleConverterMock).convert(dependency1.getExcludeRules().iterator().next()); will(returnValue(mavenExclude));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, WrapUtil.toSet(someConfigurationStub));
        assertEquals(1, actualMavenDependencies.size());
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        assertEquals(actualMavenDependencies.get(0).getMavenExcludes(), WrapUtil.toList(mavenExclude));
        assertEquals(actualMavenDependencies.get(0).getScope(), COMPILE_CONF_NAME);

    }
}
