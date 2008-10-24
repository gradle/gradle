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
package org.gradle.api.internal.dependencies.maven.dependencies;

import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.gradle.util.WrapUtil;
import org.gradle.util.HelperUtil;
import org.gradle.api.internal.dependencies.maven.dependencies.MavenDependency;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultPomModuleDescriptorDependenciesConverter;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultMavenDependency;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;
import org.gradle.api.dependencies.maven.MavenPom;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import org.hamcrest.Matchers;

import java.util.List;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultPomModuleDescriptorDependenciesConverterTest {
    private DefaultPomModuleDescriptorDependenciesConverter dependenciesConverter;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;
    private ExcludeRuleConverter excludeRuleConverterMock;

    private MavenPom pomMock;
    private DefaultDependencyDescriptor dependencyDescriptor1;
    private DefaultDependencyDescriptor dependencyDescriptor2;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        dependencyDescriptor1 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "name1", "rev1"), false);
        dependencyDescriptor2 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org2", "name2", "rev2"), false);
        dependencyDescriptor1.addDependencyConfiguration("compileConf", "default");
        dependencyDescriptor2.addDependencyConfiguration("testCompileConf", "default");
        pomMock = context.mock(MavenPom.class);
        conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        excludeRuleConverterMock = context.mock(ExcludeRuleConverter.class);
        dependenciesConverter = new DefaultPomModuleDescriptorDependenciesConverter(excludeRuleConverterMock);
        context.checking(new Expectations() {{
            allowing(pomMock).getScopeMappings();
            will(returnValue(conf2ScopeMappingContainerMock));
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor2.getModuleConfigurations()); will(returnValue("test"));
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor1.getModuleConfigurations()); will(returnValue("compile"));
        }});
    }

    @Test
    public void init() {
        assertSame(excludeRuleConverterMock, dependenciesConverter.getExcludeRuleConverter());
    }

    @Test
    public void convert() {
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
            allowing(pomMock).getDependencies();
            will(returnValue(WrapUtil.toList(dependencyDescriptor1, dependencyDescriptor2)));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock);
        assertEquals(2, actualMavenDependencies.size());
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org2", "name2", "rev2", null, "test")));
    }

    @Test
    public void convertWithUnMappedConfAndSkipTrue() {
        final DefaultDependencyDescriptor dependencyDescriptor3 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org3", "name3", "rev3"), false);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(true));
            allowing(pomMock).getDependencies();
            will(returnValue(WrapUtil.toList(dependencyDescriptor1, dependencyDescriptor2, dependencyDescriptor3)));
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor3.getModuleConfigurations()); will(returnValue(null));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock);
        assertEquals(2, actualMavenDependencies.size());
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org2", "name2", "rev2", null, "test")));
    }

    @Test
    public void convertWithUnMappedConfAndSkipFalse() {
        final DefaultDependencyDescriptor dependencyDescriptor3 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org3", "name3", "rev3"), false);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor3.getModuleConfigurations()); will(returnValue(null));
            allowing(pomMock).getDependencies();
            will(returnValue(WrapUtil.toList(dependencyDescriptor1, dependencyDescriptor2, dependencyDescriptor3)));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock);
        assertEquals(3, actualMavenDependencies.size());
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org2", "name2", "rev2", null, "test")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org3", "name3", "rev3", null, null)));
    }

    @Test
    public void convertWithConvertableExcludes() {
        final DefaultExcludeRule testExcludeRule = HelperUtil.getTestExcludeRules();
        final DefaultMavenExclude mavenExclude = new DefaultMavenExclude("a", "b");
        dependencyDescriptor1.addExcludeRule("compileConf", testExcludeRule);
        context.checking(new Expectations() {{
           allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
           allowing(excludeRuleConverterMock).convert(testExcludeRule); will(returnValue(mavenExclude));
           allowing(pomMock).getDependencies(); will(returnValue(WrapUtil.toList(dependencyDescriptor1)));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock);
        assertEquals(1, actualMavenDependencies.size());
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        assertEquals(actualMavenDependencies.get(0).getMavenExcludes(), WrapUtil.toList(mavenExclude));
        assertEquals(actualMavenDependencies.get(0).getScope(), "compile");

    }
}
