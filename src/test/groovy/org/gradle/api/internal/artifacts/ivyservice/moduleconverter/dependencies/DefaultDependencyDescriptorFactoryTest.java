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
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
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
import java.util.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultDependencyDescriptorFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    
    private static final Map DUMMY_MODULE_REGISTRY = Collections.unmodifiableMap(new HashMap());
    private static final String TEST_CONF = "conf";
    private static final String TEST_DEP_CONF = "depconf1";
    private static final ModuleDescriptor TEST_PARENT = HelperUtil.createModuleDescriptor(WrapUtil.toSet(TEST_CONF));
    private static final ExcludeRule TEST_EXCLUDE_RULE = new org.gradle.api.internal.artifacts.DefaultExcludeRule(WrapUtil.toMap("org", "testOrg"));
    private static final org.apache.ivy.core.module.descriptor.ExcludeRule TEST_IVY_EXCLUDE_RULE = HelperUtil.getTestExcludeRule();

    private ExcludeRuleConverter excludeRuleConverterStub = context.mock(ExcludeRuleConverter.class);
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
        DefaultProjectDependency projectDependency = (DefaultProjectDependency) setUpDependency(new DefaultProjectDependency(dependencyProject, TEST_DEP_CONF));

        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor)
                dependencyDescriptorFactory.createDependencyDescriptor(TEST_CONF, TEST_PARENT, projectDependency, DUMMY_MODULE_REGISTRY);

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

        final Dependency dependencyDependency = context.mock(Dependency.class, "dependencyDependency"); 
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

        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor)
                dependencyDescriptorFactory.createDependencyDescriptor(TEST_CONF, TEST_PARENT, clientModule, testModuleRegistry);
        assertDependencyDescriptorHasFixtureValuesForExternalDependencies(dependencyDescriptor, testModuleRevisionId);
        assertFalse(dependencyDescriptor.isChanging());
    }
    
    @Test
    public void testCreateFromModuleDependency() {
        DefaultModuleDependency moduleDependency = ((DefaultModuleDependency)
                setUpExternalDependency(new DefaultModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF))).setChanging(true);

        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor)
                dependencyDescriptorFactory.createDependencyDescriptor(TEST_CONF, TEST_PARENT, moduleDependency, DUMMY_MODULE_REGISTRY);

        assertEquals(moduleDependency.isChanging(), dependencyDescriptor.isChanging());
        checkModuleDependency(dependencyDescriptor, moduleDependency);
    }

    private ExternalDependency setUpExternalDependency(ExternalDependency dependency) {
        return ((ExternalDependency) setUpDependency(dependency)).setForce(true);
    }

    private Dependency setUpDependency(Dependency dependency) {
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
        assertThat(dependencyDescriptor.getParentRevisionId(), equalTo(TEST_PARENT.getModuleRevisionId()));
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


    private void checkModuleDependency(DefaultDependencyDescriptor dependencyDescriptor, DefaultModuleDependency dependency) {
        assertEquals(createModuleRevisionIdFromDependency(dependency, new HashMap()),
                dependencyDescriptor.getDependencyRevisionId());
        assertDependencyDescriptorHasFixtureValuesForExternalDependencies(dependencyDescriptor,
                createModuleRevisionIdFromNonClientModuleDependency(dependency));
    }

    private ModuleRevisionId createModuleRevisionIdFromNonClientModuleDependency(Dependency dependency) {
        return createModuleRevisionIdFromDependency(dependency, new HashMap());
    }

    private ModuleRevisionId createModuleRevisionIdFromDependency(Dependency dependency, Map extraAttributes) {
        return ModuleRevisionId.newInstance(dependency.getGroup(), dependency.getName(), dependency.getVersion(), extraAttributes);
    }
}

