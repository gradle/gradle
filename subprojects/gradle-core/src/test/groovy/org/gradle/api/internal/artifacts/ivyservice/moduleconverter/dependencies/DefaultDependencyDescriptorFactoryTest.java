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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultDependencyDescriptorFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    
    private static final Map DUMMY_MODULE_REGISTRY = Collections.unmodifiableMap(new HashMap());
    private static final String TEST_CONF = "conf";
    private static final String TEST_DEP_CONF = "depconf1";
    private static final ExcludeRule TEST_EXCLUDE_RULE = new org.gradle.api.internal.artifacts.DefaultExcludeRule(WrapUtil.toMap("org", "testOrg"));
    private static final org.apache.ivy.core.module.descriptor.ExcludeRule TEST_IVY_EXCLUDE_RULE = HelperUtil.getTestExcludeRule();
    private ExcludeRuleConverter excludeRuleConverterStub = context.mock(ExcludeRuleConverter.class);

    private final DefaultModuleDescriptor moduleDescriptor = HelperUtil.createModuleDescriptor(WrapUtil.toSet(TEST_CONF));
    private DefaultDependencyDescriptorFactory dependencyDescriptorFactory;
    private DefaultDependencyArtifact artifact = new DefaultDependencyArtifact("name", "type", null, null, null);
    private DefaultDependencyArtifact artifactWithClassifiers = new DefaultDependencyArtifact("name2", "type2", "ext2", "classifier2", "http://www.url2.com");

    @Before
    public void setUp() {
        dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory();
        dependencyDescriptorFactory.setExcludeRuleConverter(excludeRuleConverterStub);
        context.checking(new Expectations() {{
            allowing(excludeRuleConverterStub).createExcludeRule(TEST_CONF, TEST_EXCLUDE_RULE);
            will(returnValue(TEST_IVY_EXCLUDE_RULE));
        }});
    }

    @Test
    public void testCreateFromProjectDependency() {
        String dependencyProjectName = "depProject";
        final ModuleRevisionId testModuleRevisionId = ModuleRevisionId.newInstance(
                Project.DEFAULT_GROUP, dependencyProjectName, Project.DEFAULT_VERSION, new HashMap());
        final AbstractProject dependencyProject = HelperUtil.createRootProject(new File(dependencyProjectName));
        DefaultProjectDependency projectDependency = (DefaultProjectDependency) setUpDependency(new DefaultProjectDependency(dependencyProject, TEST_DEP_CONF, null));

        dependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, projectDependency, DUMMY_MODULE_REGISTRY);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];

        assertDependencyDescriptorHasCommonFixtureValues(dependencyDescriptor);
        assertTrue(dependencyDescriptor.isChanging());
        assertFalse(dependencyDescriptor.isForce());
        assertEquals(testModuleRevisionId,
                dependencyDescriptor.getDependencyRevisionId());
    }

    @Test
    public void testCreateFromClientModule() {
        dependencyDescriptorFactory.setClientModuleDescriptorFactory(context.mock(ClientModuleDescriptorFactory.class));
        final HashMap testModuleRegistry = new HashMap();
        final DefaultClientModule clientModule = (DefaultClientModule) setUpExternalDependency(
                new DefaultClientModule("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF));

        final ModuleDependency dependencyDependency = context.mock(ModuleDependency.class, "dependencyDependency");
        clientModule.addDependency(dependencyDependency);
        final ModuleRevisionId testModuleRevisionId = createModuleRevisionIdFromDependency(clientModule, WrapUtil.toMap(ClientModule.CLIENT_MODULE_KEY, clientModule.getId()));
        context.checking(new Expectations() {{
            allowing(dependencyDescriptorFactory.getClientModuleDescriptorFactory()).createModuleDescriptor(
                    testModuleRevisionId,
                    WrapUtil.toSet(dependencyDependency),
                    dependencyDescriptorFactory,
                    testModuleRegistry
            );
        }});

        dependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, clientModule, testModuleRegistry);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertDependencyDescriptorHasFixtureValuesForExternalDependencies(dependencyDescriptor, testModuleRevisionId);
        assertFalse(dependencyDescriptor.isChanging());
    }
    
    @Test
    public void testCreateFromModuleDependency() {
        DefaultExternalModuleDependency moduleDependency = ((DefaultExternalModuleDependency)
                setUpExternalDependency(new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF))).setChanging(true);

        dependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, moduleDependency, DUMMY_MODULE_REGISTRY);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];

        assertEquals(moduleDependency.isChanging(), dependencyDescriptor.isChanging());
        checkModuleDependency(dependencyDescriptor, moduleDependency);
    }

    @Test
    public void testCreateWithTwoDependenciesOfSameModuleRevisionId() {
        String otherDependencyConfiguration = TEST_DEP_CONF + "X";
        DefaultExternalModuleDependency moduleDependency = ((DefaultExternalModuleDependency)
                setUpExternalDependency(new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF))).setChanging(true);
        DefaultExternalModuleDependency moduleDependency2 = ((DefaultExternalModuleDependency)
                setUpExternalDependency(new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", otherDependencyConfiguration))).setChanging(true);

        dependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, moduleDependency, DUMMY_MODULE_REGISTRY);
        dependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, moduleDependency2, DUMMY_MODULE_REGISTRY);
        assertThat(moduleDescriptor.getDependencies().length, equalTo(1));

        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertThat(dependencyDescriptor.getDependencyConfigurations(TEST_CONF), Matchers.hasItemInArray(TEST_DEP_CONF));
        assertThat(dependencyDescriptor.getDependencyConfigurations(TEST_CONF), Matchers.hasItemInArray(otherDependencyConfiguration));
        assertThat(dependencyDescriptor.getDependencyConfigurations(TEST_CONF).length, equalTo(2));
        assertEquals(moduleDependency.isChanging(), dependencyDescriptor.isChanging());
    }

    @Test
    public void testCreateFromModuleDependencyWithNullGroupAndNullVersion() {
        DefaultExternalModuleDependency moduleDependency = ((DefaultExternalModuleDependency)
                setUpExternalDependency(new DefaultExternalModuleDependency(null, "gradle-core", null, TEST_DEP_CONF))).setChanging(true);

        dependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, moduleDependency, DUMMY_MODULE_REGISTRY);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];

        assertEquals(moduleDependency.isChanging(), dependencyDescriptor.isChanging());
        checkModuleDependency(dependencyDescriptor, moduleDependency);
    }

    private ExternalDependency setUpExternalDependency(ExternalDependency dependency) {
        return ((ExternalDependency) setUpDependency(dependency)).setForce(true);
    }

    private Dependency setUpDependency(ModuleDependency dependency) {
        return dependency.addArtifact(artifact).
                addArtifact(artifactWithClassifiers).
                exclude(TEST_EXCLUDE_RULE.getExcludeArgs()).
                setTransitive(true);
    }

    private void assertDependencyDescriptorHasFixtureValuesForExternalDependencies(DefaultDependencyDescriptor dependencyDescriptor, ModuleRevisionId moduleRevisionId) {
        assertDependencyDescriptorHasCommonFixtureValues(dependencyDescriptor);
        assertEquals(dependencyDescriptor.isForce(), dependencyDescriptor.isForce());
    }

    private void assertDependencyDescriptorHasCommonFixtureValues(DefaultDependencyDescriptor dependencyDescriptor) {
        assertThat(dependencyDescriptor.getParentRevisionId(), equalTo(moduleDescriptor.getModuleRevisionId()));
        assertEquals(TEST_IVY_EXCLUDE_RULE, dependencyDescriptor.getExcludeRules(TEST_CONF)[0]);
        assertThat(dependencyDescriptor.getDependencyConfigurations(TEST_CONF), equalTo(WrapUtil.toArray(TEST_DEP_CONF)));
        assertThat(dependencyDescriptor.isTransitive(), equalTo(true));
        assertDependencyDescriptorHasArtifacts(dependencyDescriptor);
    }

    private void assertDependencyDescriptorHasArtifacts(DefaultDependencyDescriptor dependencyDescriptor) {
        List<DependencyArtifactDescriptor> artifactDescriptors = WrapUtil.toList(dependencyDescriptor.getDependencyArtifacts(TEST_CONF));
        assertThat(artifactDescriptors.size(), equalTo(2));

        
        DependencyArtifactDescriptor artifactDescriptorWithoutClassifier = findDescriptor(artifactDescriptors, artifact);
        assertEquals(new HashMap(), artifactDescriptorWithoutClassifier.getExtraAttributes());
        assertEquals(null, artifactDescriptorWithoutClassifier.getUrl());
        compareArtifacts(artifact, artifactDescriptorWithoutClassifier);
        assertEquals(artifact.getType(), artifactDescriptorWithoutClassifier.getExt());

        DependencyArtifactDescriptor artifactDescriptorWithClassifierAndConfs = findDescriptor(artifactDescriptors, artifactWithClassifiers);
        assertEquals(WrapUtil.toMap(Dependency.CLASSIFIER, artifactWithClassifiers.getClassifier()), artifactDescriptorWithClassifierAndConfs.getExtraAttributes());
        compareArtifacts(artifactWithClassifiers, artifactDescriptorWithClassifierAndConfs);
        assertEquals(artifactWithClassifiers.getExtension(), artifactDescriptorWithClassifierAndConfs.getExt());
        try {
            assertEquals(new URL(artifactWithClassifiers.getUrl()), artifactDescriptorWithClassifierAndConfs.getUrl());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private DependencyArtifactDescriptor findDescriptor(List<DependencyArtifactDescriptor> artifactDescriptors, DefaultDependencyArtifact dependencyArtifact) {
        for (DependencyArtifactDescriptor artifactDescriptor : artifactDescriptors) {
            if (artifactDescriptor.getName().equals(dependencyArtifact.getName())) {
                return artifactDescriptor;
            }
        }
        throw new RuntimeException("Descriptor could not be found");
    }

    private void compareArtifacts(DependencyArtifact artifact, DependencyArtifactDescriptor artifactDescriptor) {
        assertEquals(artifact.getName(), artifactDescriptor.getName());
        assertEquals(artifact.getType(), artifactDescriptor.getType());
    }


    private void checkModuleDependency(DefaultDependencyDescriptor dependencyDescriptor, DefaultExternalModuleDependency dependency) {
        assertEquals(createModuleRevisionIdFromDependency(dependency, new HashMap()),
                dependencyDescriptor.getDependencyRevisionId());
        assertDependencyDescriptorHasFixtureValuesForExternalDependencies(dependencyDescriptor,
                createModuleRevisionIdFromNonClientModuleDependency(dependency));
    }

    private ModuleRevisionId createModuleRevisionIdFromNonClientModuleDependency(Dependency dependency) {
        return createModuleRevisionIdFromDependency(dependency, new HashMap());
    }

    private ModuleRevisionId createModuleRevisionIdFromDependency(Dependency dependency, Map extraAttributes) {
        return ModuleRevisionId.newInstance(GUtil.elvis(dependency.getGroup(), ""), dependency.getName(), GUtil.elvis(dependency.getVersion(), "")
                , extraAttributes);
    }
}

