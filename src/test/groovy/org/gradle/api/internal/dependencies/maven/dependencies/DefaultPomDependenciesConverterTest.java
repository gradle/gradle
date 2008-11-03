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
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultPomDependenciesConverter;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultMavenDependency;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;
import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.api.dependencies.ArtifactDependency;
import org.apache.ivy.core.module.descriptor.*;
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
public class DefaultPomDependenciesConverterTest {
    private DefaultPomDependenciesConverter dependenciesConverter;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;
    private ExcludeRuleConverter excludeRuleConverterMock;

    private MavenPom pomMock;
    private DefaultDependencyDescriptor dependencyDescriptor1;
    private DefaultDependencyDescriptor dependencyDescriptor2;
    private DefaultDependencyDescriptor dependencyDescriptor3;
    private DefaultDependencyArtifactDescriptor artifactDescriptor1;
    private DefaultDependencyArtifactDescriptor artifactDescriptor2;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        dependencyDescriptor1 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "name1", "rev1"), false);
        dependencyDescriptor2 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org2", "name2", "rev2"), false);
        dependencyDescriptor3 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org3", "name3", "rev3"), false);
        dependencyDescriptor1.addDependencyConfiguration("compileConf", "default");
        dependencyDescriptor2.addDependencyConfiguration("testCompileConf", "default");
        artifactDescriptor1 = new DefaultDependencyArtifactDescriptor(dependencyDescriptor3, "artifactName31", "type31", "ext", null, null);
        artifactDescriptor2 = new DefaultDependencyArtifactDescriptor(dependencyDescriptor3, "artifactName32", "type32", "ext", null, null);
        dependencyDescriptor3.addDependencyConfiguration("compileConf", "default");
        dependencyDescriptor3.addDependencyConfiguration("testCompileConf", "default");
        dependencyDescriptor3.addDependencyArtifact("compileConf", artifactDescriptor1);
        dependencyDescriptor3.addDependencyArtifact("testCompileConf", artifactDescriptor2);
        pomMock = context.mock(MavenPom.class);
        conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        excludeRuleConverterMock = context.mock(ExcludeRuleConverter.class);
        dependenciesConverter = new DefaultPomDependenciesConverter(excludeRuleConverterMock);
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
        List<DependencyDescriptor> testDependencies = WrapUtil.<DependencyDescriptor>toList(dependencyDescriptor1, dependencyDescriptor2, dependencyDescriptor3);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, testDependencies);
        assertEquals(4, actualMavenDependencies.size());
        chechCommonMavenDependencies(actualMavenDependencies);
    }

    @Test
    public void convertWithUnMappedConfAndSkipTrue() {
        final DefaultDependencyDescriptor dependencyDescriptor4 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org3", "name3", "rev3"), false);
        List<DependencyDescriptor> testDependencies = WrapUtil.<DependencyDescriptor>toList(dependencyDescriptor1, dependencyDescriptor2,
                dependencyDescriptor3, dependencyDescriptor4);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(true));
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor4.getModuleConfigurations()); will(returnValue(null));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, testDependencies);
        assertEquals(4, actualMavenDependencies.size());
        chechCommonMavenDependencies(actualMavenDependencies);
    }

    @Test
    public void convertWithUnMappedConfAndSkipFalse() {
        final DefaultDependencyDescriptor dependencyDescriptor4 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org3", "name3", "rev3"), false);
        List<DependencyDescriptor> testDependencies = WrapUtil.<DependencyDescriptor>toList(dependencyDescriptor1, dependencyDescriptor2,
                dependencyDescriptor3, dependencyDescriptor4);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor4.getModuleConfigurations()); will(returnValue(null));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, testDependencies);
        assertEquals(5, actualMavenDependencies.size());
        chechCommonMavenDependencies(actualMavenDependencies);
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org3", "name3", "rev3", null, null)));
    }

    private void chechCommonMavenDependencies(List<MavenDependency> actualMavenDependencies) {
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org2", "name2", "rev2", null, "test")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org3", "artifactName31", "rev3", "type31", "compile")));
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org3", "artifactName32", "rev3", "type32", "test")));
    }

    @Test
    public void convertWithConvertableExcludes() {
        List<DependencyDescriptor> testDependencies = WrapUtil.<DependencyDescriptor>toList(dependencyDescriptor1);
        final DefaultExcludeRule testExcludeRule = HelperUtil.getTestExcludeRule();
        final DefaultMavenExclude mavenExclude = new DefaultMavenExclude("a", "b");
        dependencyDescriptor1.addExcludeRule("compileConf", testExcludeRule);
        context.checking(new Expectations() {{
           allowing(conf2ScopeMappingContainerMock).isSkipUnmappedConfs(); will(returnValue(false));
           allowing(excludeRuleConverterMock).convert(testExcludeRule); will(returnValue(mavenExclude));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(pomMock, testDependencies);
        assertEquals(1, actualMavenDependencies.size());
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        assertEquals(actualMavenDependencies.get(0).getMavenExcludes(), WrapUtil.toList(mavenExclude));
        assertEquals(actualMavenDependencies.get(0).getScope(), "compile");

    }
}
