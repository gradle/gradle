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
package org.gradle.api.internal.dependencies.ivy2Maven.dependencies;

import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.gradle.util.WrapUtil;
import org.gradle.util.HelperUtil;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.MavenDependency;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.DefaultPomModuleDescriptorDependenciesConverter;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.DefaultMavenDependency;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.Conf2ScopeMappingContainer;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.hamcrest.Matchers;

import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultPomModuleDescriptorDependenciesConverterTest {
    private DefaultPomModuleDescriptorDependenciesConverter dependenciesConverter;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;
    private ExcludeRuleConverter excludeRuleConverterMock;

    private JUnit4Mockery context = new JUnit4Mockery();
    private ModuleDescriptor moduleDescriptorMock;
    private DefaultDependencyDescriptor dependencyDescriptor1;
    private DefaultDependencyDescriptor dependencyDescriptor2;

    @Before
    public void setUp() {
        dependencyDescriptor1 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "name1", "rev1"), false);
        dependencyDescriptor2 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org2", "name2", "rev2"), false);
        dependencyDescriptor1.addDependencyConfiguration("compileConf", "default");
        dependencyDescriptor2.addDependencyConfiguration("testCompileConf", "default");
        moduleDescriptorMock = context.mock(ModuleDescriptor.class);
        conf2ScopeMappingContainerMock = context.mock(Conf2ScopeMappingContainer.class);
        excludeRuleConverterMock = context.mock(ExcludeRuleConverter.class);
        dependenciesConverter = new DefaultPomModuleDescriptorDependenciesConverter(excludeRuleConverterMock);
        context.checking(new Expectations() {{
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor1.getModuleConfigurations()); will(returnValue("compile"));
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor2.getModuleConfigurations()); will(returnValue("test"));
        }});
    }

    @Test
    public void init() {
        assertSame(excludeRuleConverterMock, dependenciesConverter.getExcludeRuleConverter());
    }

    @Test
    public void convert() {
        context.checking(new Expectations() {{
            allowing(moduleDescriptorMock).getDependencies();
            will(returnValue(WrapUtil.toArray(dependencyDescriptor1, dependencyDescriptor2)));  
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(moduleDescriptorMock, false,
                conf2ScopeMappingContainerMock);
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
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor3.getModuleConfigurations()); will(returnValue(null));
            allowing(moduleDescriptorMock).getDependencies();
            will(returnValue(WrapUtil.toArray(dependencyDescriptor1, dependencyDescriptor2, dependencyDescriptor3)));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(moduleDescriptorMock, true,
                conf2ScopeMappingContainerMock);
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
            allowing(conf2ScopeMappingContainerMock).getScope(dependencyDescriptor3.getModuleConfigurations()); will(returnValue(null));
            allowing(moduleDescriptorMock).getDependencies();
            will(returnValue(WrapUtil.toArray(dependencyDescriptor1, dependencyDescriptor2, dependencyDescriptor3)));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(moduleDescriptorMock, false,
                conf2ScopeMappingContainerMock);
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
           allowing(excludeRuleConverterMock).convert(testExcludeRule); will(returnValue(mavenExclude));
           allowing(moduleDescriptorMock).getDependencies(); will(returnValue(WrapUtil.toArray(dependencyDescriptor1)));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(moduleDescriptorMock, false,
                conf2ScopeMappingContainerMock);
        assertEquals(1, actualMavenDependencies.size());
        Assert.assertThat(actualMavenDependencies,
                Matchers.hasItem((MavenDependency) DefaultMavenDependency.newInstance("org1", "name1", "rev1", null, "compile")));
        assertEquals(actualMavenDependencies.get(0).getMavenExcludes(), WrapUtil.toList(mavenExclude));
        assertEquals(actualMavenDependencies.get(0).getScope(), "compile");

    }

    @Test
    public void convertWithCustomMavenDependencies() {
        DefaultMavenDependency customMavenDependency = null; //new DefaultMavenDependency.newInstance();
        context.checking(new Expectations() {{
           allowing(moduleDescriptorMock).getDependencies(); will(returnValue(WrapUtil.toArray(dependencyDescriptor1)));
        }});
        List<MavenDependency> actualMavenDependencies = dependenciesConverter.convert(moduleDescriptorMock, false,
                conf2ScopeMappingContainerMock);
    }
}
