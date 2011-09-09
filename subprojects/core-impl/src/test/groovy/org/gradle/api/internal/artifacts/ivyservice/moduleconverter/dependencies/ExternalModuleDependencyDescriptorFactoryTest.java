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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.*;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
public class ExternalModuleDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    ExternalModuleDependencyDescriptorFactory externalModuleDependencyDescriptorFactory =
            new ExternalModuleDependencyDescriptorFactory(excludeRuleConverterStub);
    
    @Test
    public void canConvert() {
        assertThat(externalModuleDependencyDescriptorFactory.canConvert(context.mock(ProjectDependency.class)), Matchers.equalTo(false));
        assertThat(externalModuleDependencyDescriptorFactory.canConvert(context.mock(ExternalModuleDependency.class)), Matchers.equalTo(true));
    }

    @Test
    public void testAddWithNullGroupAndNullVersionShouldHaveEmptyStringModuleRevisionValues() {
        ModuleDependency dependency = new DefaultExternalModuleDependency(null, "gradle-core", null, TEST_DEP_CONF);
        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, dependency);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertThat(dependencyDescriptor.getDependencyRevisionId(), equalTo(IvyUtil.createModuleRevisionId(dependency)));
    }

    @Test
    public void testCreateFromModuleDependency() {
        DefaultExternalModuleDependency moduleDependency = new DefaultExternalModuleDependency("org.gradle",
                "gradle-core", "1.0", TEST_DEP_CONF);
        setUpDependency(moduleDependency);

        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor,
                moduleDependency);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor
                .getDependencies()[0];

        assertEquals(moduleDependency.isChanging(), dependencyDescriptor.isChanging());
        assertEquals(dependencyDescriptor.isForce(), moduleDependency.isForce());
        assertEquals(IvyUtil.createModuleRevisionId(moduleDependency), dependencyDescriptor.getDependencyRevisionId());
        assertDependencyDescriptorHasCommonFixtureValues(dependencyDescriptor);
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor() {
        ModuleDependency dependency1 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        ModuleDependency dependency2 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);

        assertThataddDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor(
                dependency1, dependency2, externalModuleDependencyDescriptorFactory
        );
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdAndSameClassifiersShouldBePartOfSameDependencyDescriptor() {
        ExternalDependency dependency1 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency1, null, "jdk14");

        ExternalDependency dependency2 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency2, null, "jdk14");

        assertThataddDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor(
                dependency1, dependency2, externalModuleDependencyDescriptorFactory
        );
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdAndDifferentClassifiersShouldHaveArtifactsCombinedIntoSameDependencyDescriptor() {
        ExternalDependency dependency1 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency1, null, "jdk14");

        ExternalDependency dependency2 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency2, null, "jdk15");

        assertThataddDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor(
                dependency1, dependency2, externalModuleDependencyDescriptorFactory
        );
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertThat(dependencyDescriptor.getAllDependencyArtifacts().length, equalTo(2));
        assertThat(dependencyDescriptor.getAllDependencyArtifacts(), Matchers.hasItemInArray(artifactDescriptor("jdk14", dependencyDescriptor)));
        assertThat(dependencyDescriptor.getAllDependencyArtifacts(), Matchers.hasItemInArray(artifactDescriptor("jdk15", dependencyDescriptor)));
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdShouldHaveForceTransitiveAndChangingFlagsMerged() {
        ExternalModuleDependency dependency1 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        dependency1.setForce(false);
        dependency1.setTransitive(false);
        dependency1.setChanging(false);

        ExternalModuleDependency dependency2 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);
        dependency2.setForce(false);
        dependency2.setTransitive(false);
        dependency2.setChanging(false);

        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, dependency1);
        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, dependency2);

        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertThat(dependencyDescriptor.isForce(), is(false));
        assertThat(dependencyDescriptor.isTransitive(), is(false));
        assertThat(dependencyDescriptor.isChanging(), is(false));

        ExternalModuleDependency dependency3 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);
        dependency3.setForce(true);
        dependency3.setTransitive(true);
        dependency3.setChanging(true);
        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, dependency3);

        dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertThat(dependencyDescriptor.isForce(), is(true));
        assertThat(dependencyDescriptor.isTransitive(), is(true));
        assertThat(dependencyDescriptor.isChanging(), is(true));
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdShouldHaveExcludeRulesMerged() {
        ExcludeRule excludeRule = new DefaultExcludeRule(WrapUtil.toMap("other", "org"));
        org.apache.ivy.core.module.descriptor.ExcludeRule ivyExcludeRule = HelperUtil.getTestExcludeRule("other");
        expectExcludeRuleConversion(excludeRule, ivyExcludeRule);

        ExternalDependency excludingBoth = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        excludingBoth.exclude(TEST_EXCLUDE_RULE.getExcludeArgs());
        excludingBoth.exclude(excludeRule.getExcludeArgs());
        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, excludingBoth);

        // Has both exclude rules
        assertDescriptorHasIvyExcludeRules(TEST_IVY_EXCLUDE_RULE, ivyExcludeRule);

        ExternalDependency excludingOne = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);
        excludingOne.exclude(excludeRule.getExcludeArgs());
        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, excludingOne);

        // Has intersection of exclude rules
        assertDescriptorHasIvyExcludeRules(ivyExcludeRule);

        ExternalDependency excludingNone = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);
        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, excludingNone);

        // Has no exclude rules
        assertDescriptorHasIvyExcludeRules();
    }

    private void assertDescriptorHasIvyExcludeRules(org.apache.ivy.core.module.descriptor.ExcludeRule... rules) {
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertThat(dependencyDescriptor.getExcludeRules(TEST_CONF), equalTo(rules));
    }

    private DependencyArtifactDescriptor artifactDescriptor(String classifier, DependencyDescriptor dd) {
        return new DefaultDependencyArtifactDescriptor(dd, "gradle-core", "jar", "jar", null, WrapUtil.toMap("classifier", classifier));
    }

}
