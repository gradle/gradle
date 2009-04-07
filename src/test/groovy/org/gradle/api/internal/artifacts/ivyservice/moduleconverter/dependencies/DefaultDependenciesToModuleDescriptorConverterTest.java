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
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.plugins.conflict.LatestConflictManager;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.IvyConverterTestUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.gradle.util.WrapUtil.*;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultDependenciesToModuleDescriptorConverterTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private static final ExcludeRule GRADLE_EXCLUDE_RULE_DUMMY_1 = new DefaultExcludeRule(toMap("org", "testOrg"));
    private static final ExcludeRule GRADLE_EXCLUDE_RULE_DUMMY_2 = new DefaultExcludeRule(toMap("org2", "testOrg2"));
    private static final Map CLIENT_MODULE_REGISTRY_DUMMY = Collections.emptyMap();

    private Dependency dependencyDummy1 = context.mock(Dependency.class, "dep1");
    private Dependency dependencyDummy2 = context.mock(Dependency.class, "dep2");
    private Dependency similarDependency1 = HelperUtil.createDependency("group", "name", "version");
    private Dependency similarDependency2 = HelperUtil.createDependency("group", "name", "version");
    private Dependency similarDependency3 = HelperUtil.createDependency("group", "name", "version");
    private DependencyDescriptor dependencyDescriptorDummy1 = context.mock(DependencyDescriptor.class, "descr1");
    private DependencyDescriptor dependencyDescriptorDummy2 = context.mock(DependencyDescriptor.class, "descr2");
    private DependencyDescriptor dependencyDescriptorDummySimilarDependency1 = context.mock(DependencyDescriptor.class, "descrSimilarDependency1");
    private DependencyDescriptor dependencyDescriptorDummySimilarDependency2 = context.mock(DependencyDescriptor.class, "descrSimilarDependency2");
    private DependencyDescriptor dependencyDescriptorDummySimilarDependency3 = context.mock(DependencyDescriptor.class, "descrSimilarDependency3");
    private org.apache.ivy.core.module.descriptor.ExcludeRule ivyExcludeRuleStub_1 = context.mock(org.apache.ivy.core.module.descriptor.ExcludeRule.class, "rule1");
    private org.apache.ivy.core.module.descriptor.ExcludeRule ivyExcludeRuleStub_2 = context.mock(org.apache.ivy.core.module.descriptor.ExcludeRule.class, "rule2");

    private DependencyDescriptorFactory dependencyDescriptorFactoryStub = context.mock(DependencyDescriptorFactory.class);
    private ExcludeRuleConverter excludeRuleConverterStub = context.mock(ExcludeRuleConverter.class);

    @Test
    public void testAddDependencyDescriptors() {
        DefaultDependenciesToModuleDescriptorConverter converter = new DefaultDependenciesToModuleDescriptorConverter();
        converter.setDependencyDescriptorFactory(dependencyDescriptorFactoryStub);
        converter.setExcludeRuleConverter(excludeRuleConverterStub);
        Configuration configurationStub1 = createNamedConfigurationStubWithDependenciesAndExcludeRules("conf1", GRADLE_EXCLUDE_RULE_DUMMY_1, dependencyDummy1, similarDependency1);
        Configuration configurationStub2 = createNamedConfigurationStubWithDependenciesAndExcludeRules("conf2", GRADLE_EXCLUDE_RULE_DUMMY_2, dependencyDummy2, similarDependency2);
        Configuration configurationStub3 = createNamedConfigurationStubWithDependenciesAndExcludeRules("conf3", null, similarDependency3);
        DefaultModuleDescriptor moduleDescriptor = HelperUtil.createModuleDescriptor(toSet(configurationStub1.getName(),
                configurationStub2.getName()));
        associateDependencyWithDescriptor(dependencyDummy1, dependencyDescriptorDummy1, moduleDescriptor, configurationStub1);
        associateDependencyWithDescriptor(dependencyDummy2, dependencyDescriptorDummy2, moduleDescriptor, configurationStub2);
        associateDependencyWithDescriptor(similarDependency1, dependencyDescriptorDummySimilarDependency1, moduleDescriptor, configurationStub1);
        associateDependencyWithDescriptor(similarDependency2, dependencyDescriptorDummySimilarDependency2, moduleDescriptor, configurationStub2);
        associateDependencyWithDescriptor(similarDependency3, dependencyDescriptorDummySimilarDependency3, moduleDescriptor, configurationStub3);
        associateGradleExcludeRuleWithIvyExcludeRule(GRADLE_EXCLUDE_RULE_DUMMY_1, ivyExcludeRuleStub_1, configurationStub1);
        associateGradleExcludeRuleWithIvyExcludeRule(GRADLE_EXCLUDE_RULE_DUMMY_2, ivyExcludeRuleStub_2, configurationStub2);

        converter.addDependencyDescriptors(moduleDescriptor, toSet(configurationStub1, configurationStub2, configurationStub3), CLIENT_MODULE_REGISTRY_DUMMY);
                
        assertThat(moduleDescriptor.getDependencies().length, equalTo(5));
        assertThat(moduleDescriptor.getDependencies(), Matchers.hasItemInArray(sameInstance(dependencyDescriptorDummy1)));
        assertThat(moduleDescriptor.getDependencies(), Matchers.hasItemInArray(sameInstance(dependencyDescriptorDummy2)));
        assertThat(moduleDescriptor.getDependencies(), Matchers.hasItemInArray(sameInstance(dependencyDescriptorDummySimilarDependency1)));
        assertThat(moduleDescriptor.getDependencies(), Matchers.hasItemInArray(sameInstance(dependencyDescriptorDummySimilarDependency2)));
        assertThat(moduleDescriptor.getDependencies(), Matchers.hasItemInArray(sameInstance(dependencyDescriptorDummySimilarDependency3)));
        assertThat(moduleDescriptor.getExcludeRules(toArray(configurationStub1.getName())), equalTo(toArray(ivyExcludeRuleStub_1)));
        assertThat(moduleDescriptor.getExcludeRules(toArray(configurationStub2.getName())), equalTo(toArray(ivyExcludeRuleStub_2)));
        assertIsCorrectConflictResolver(moduleDescriptor);
        
    }

    private void assertIsCorrectConflictResolver(DefaultModuleDescriptor moduleDescriptor) {
        assertThat(moduleDescriptor.getConflictManager(new ModuleId(ExactPatternMatcher.ANY_EXPRESSION, ExactPatternMatcher.ANY_EXPRESSION)),
                instanceOf(LatestConflictManager.class));
    }

    private void associateGradleExcludeRuleWithIvyExcludeRule(final ExcludeRule gradleExcludeRule,
                                                              final org.apache.ivy.core.module.descriptor.ExcludeRule ivyExcludeRule,
                                                              final Configuration configuration) {
        context.checking(new Expectations() {{
            allowing(excludeRuleConverterStub).createExcludeRule(gradleExcludeRule);
            will(returnValue(ivyExcludeRule));

            allowing(ivyExcludeRule).getConfigurations();
            will(returnValue(WrapUtil.toArray(configuration.getName())));
        }});
    }

    private void associateDependencyWithDescriptor(final Dependency dependency, final DependencyDescriptor dependencyDescriptor,
                                                   final ModuleDescriptor parent, final Configuration configuration) {
        final String configurationName = configuration.getName();
        context.checking(new Expectations() {{
            allowing(dependencyDescriptorFactoryStub).createDependencyDescriptor(with(equal(configurationName)),
                    with(equal(parent)), with(sameInstance(dependency)), with(equal(CLIENT_MODULE_REGISTRY_DUMMY)));
            will(returnValue(dependencyDescriptor));
        }});
    }
    
    private Configuration createNamedConfigurationStubWithDependenciesAndExcludeRules(final String name, final ExcludeRule excludeRule,
                                                                                      final Dependency... dependencies) {
        final Configuration configurationStub = IvyConverterTestUtil.createNamedConfigurationStub(name, context);
        context.checking(new Expectations() {{
            allowing(configurationStub).getDependencies();
            will(returnValue(toSet(dependencies)));    

            allowing(configurationStub).getExcludeRules();
            will(returnValue(excludeRule == null ? Collections.emptySet() : toSet(excludeRule)));
        }});
        return configurationStub;
    }
}
