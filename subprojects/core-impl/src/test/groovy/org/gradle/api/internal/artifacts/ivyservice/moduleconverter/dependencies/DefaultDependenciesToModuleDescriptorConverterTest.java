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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.IvyConverterTestUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultDependenciesToModuleDescriptorConverterTest {
    private JUnit4Mockery context = new JUnit4GroovyMockery();

    private static final ExcludeRule GRADLE_EXCLUDE_RULE_DUMMY_1 = new DefaultExcludeRule("testOrg", null);
    private static final ExcludeRule GRADLE_EXCLUDE_RULE_DUMMY_2 = new DefaultExcludeRule(null, "testModule");

    private ModuleDependency dependencyDummy1 = context.mock(ModuleDependency.class, "dep1");
    private ModuleDependency dependencyDummy2 = context.mock(ModuleDependency.class, "dep2");
    private ModuleDependency similarDependency1 = createDependency("group", "name", "version");
    private ModuleDependency similarDependency2 = createDependency("group", "name", "version");
    private ModuleDependency similarDependency3 = createDependency("group", "name", "version");
    private org.apache.ivy.core.module.descriptor.ExcludeRule ivyExcludeRuleStub1 = context.mock(org.apache.ivy.core.module.descriptor.ExcludeRule.class, "rule1");
    private org.apache.ivy.core.module.descriptor.ExcludeRule ivyExcludeRuleStub2 = context.mock(org.apache.ivy.core.module.descriptor.ExcludeRule.class, "rule2");

    private DependencyDescriptorFactory dependencyDescriptorFactoryStub = context.mock(DependencyDescriptorFactory.class);
    private ExcludeRuleConverter excludeRuleConverterStub = context.mock(ExcludeRuleConverter.class);

    @Test
    public void testAddDependencyDescriptors() {
        DefaultDependenciesToModuleDescriptorConverter converter = new DefaultDependenciesToModuleDescriptorConverter(
                dependencyDescriptorFactoryStub, excludeRuleConverterStub);
        Configuration configurationStub1 = createNamedConfigurationStubWithDependenciesAndExcludeRules("conf1", GRADLE_EXCLUDE_RULE_DUMMY_1, dependencyDummy1, similarDependency1);
        Configuration configurationStub2 = createNamedConfigurationStubWithDependenciesAndExcludeRules("conf2", GRADLE_EXCLUDE_RULE_DUMMY_2, dependencyDummy2, similarDependency2);
        Configuration configurationStub3 = createNamedConfigurationStubWithDependenciesAndExcludeRules("conf3", null, similarDependency3);
        final DefaultModuleDescriptor moduleDescriptor = HelperUtil.createModuleDescriptor(toSet(configurationStub1.getName(),
                configurationStub2.getName()));
        associateDependencyWithDescriptor(dependencyDummy1, moduleDescriptor, configurationStub1);
        associateDependencyWithDescriptor(dependencyDummy2, moduleDescriptor, configurationStub2);
        associateDependencyWithDescriptor(similarDependency1, moduleDescriptor, configurationStub1);
        associateDependencyWithDescriptor(similarDependency2, moduleDescriptor, configurationStub2);
        associateDependencyWithDescriptor(similarDependency3, moduleDescriptor, configurationStub3);
        associateGradleExcludeRuleWithIvyExcludeRule(GRADLE_EXCLUDE_RULE_DUMMY_1, ivyExcludeRuleStub1, configurationStub1);
        associateGradleExcludeRuleWithIvyExcludeRule(GRADLE_EXCLUDE_RULE_DUMMY_2, ivyExcludeRuleStub2, configurationStub2);

        converter.addDependencyDescriptors(moduleDescriptor, toSet(configurationStub1, configurationStub2, configurationStub3));

        assertThat(moduleDescriptor.getExcludeRules(toArray(configurationStub1.getName())), equalTo(toArray(
                ivyExcludeRuleStub1)));
        assertThat(moduleDescriptor.getExcludeRules(toArray(configurationStub2.getName())), equalTo(toArray(
                ivyExcludeRuleStub2)));

    }

    private void associateGradleExcludeRuleWithIvyExcludeRule(final ExcludeRule gradleExcludeRule,
                                                              final org.apache.ivy.core.module.descriptor.ExcludeRule ivyExcludeRule,
                                                              final Configuration configuration) {
        final String expectedConfigurationName = configuration.getName();
        context.checking(new Expectations() {{
            allowing(excludeRuleConverterStub).createExcludeRule(expectedConfigurationName, gradleExcludeRule);
            will(returnValue(ivyExcludeRule));

            allowing(ivyExcludeRule).getConfigurations();
            will(returnValue(WrapUtil.toArray(configuration.getName())));
        }});
    }

    private void associateDependencyWithDescriptor(final ModuleDependency dependency, final DefaultModuleDescriptor parent,
                                                   final Configuration configuration) {
        context.checking(new Expectations() {{
            allowing(dependencyDescriptorFactoryStub).addDependencyDescriptor(with(equal(configuration.getName())),
                    with(equal(parent)), with(sameInstance(dependency)));
        }});
    }
    
    private Configuration createNamedConfigurationStubWithDependenciesAndExcludeRules(final String name, final ExcludeRule excludeRule,
                                                                                      final ModuleDependency... dependencies) {
        final Configuration configurationStub = IvyConverterTestUtil.createNamedConfigurationStub(name, context);
        final DependencySet dependencySet = context.mock(DependencySet.class);

        context.checking(new Expectations() {{
            allowing(configurationStub).getDependencies();
            will(returnValue(dependencySet));

            allowing(dependencySet).withType(ModuleDependency.class);
            will(returnValue(toDomainObjectSet(ModuleDependency.class, dependencies)));

            allowing(configurationStub).getExcludeRules();
            will(returnValue(excludeRule == null ? Collections.emptySet() : toSet(excludeRule)));
        }});
        return configurationStub;
    }

    ModuleDependency createDependency(String group, String name, String version) {
        return new DefaultExternalModuleDependency(group, name, version);
    }
}
