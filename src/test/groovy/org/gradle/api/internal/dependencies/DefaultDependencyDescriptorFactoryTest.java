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

package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.dependencies.*;
import org.gradle.api.dependencies.Artifact;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.gradle.util.GUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.apache.ivy.core.module.descriptor.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;

import java.util.*;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultDependencyDescriptorFactoryTest {
    private static final String TEST_CONF = "conf";
    private static final String TEST_CONF_2 = "conf2";
    private static final String TEST_CONF_3 = "conf3";
    protected static final DefaultDependencyConfigurationMappingContainer TEST_CONF_MAPPING =
        new DefaultDependencyConfigurationMappingContainer() {{
            addMasters(TEST_CONF);
    }};
    private static final Set<String> TEST_CONFS = WrapUtil.toSet(TEST_CONF, TEST_CONF_2, TEST_CONF_3);
    private static final ModuleDescriptor TEST_PARENT = HelperUtil.getTestModuleDescriptor(TEST_CONFS);
    private static final String WILDCARD_CONF = "depconf4";;

    static final boolean TEST_CHANGING = true;

    private DefaultDependencyDescriptorFactory dependencyDescriptorFactory;

    private ExcludeRuleContainer excludeRuleContainerMock;
    private DependencyConfigurationMappingContainer dependencyConfigurationMappingContainerMock;
    private JUnit4Mockery context = new JUnit4Mockery();
    private DefaultExcludeRule excludeRuleWithAllConf;
    private DefaultExcludeRule excludeRuleWithConf;
    private Map<String, List<String>> testDependencyConfigurations;
    private Artifact artifact;
    private Artifact artifactWithClassifierAndConfs;

    @Before
    public void setUp() {
        dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory();
        excludeRuleContainerMock = context.mock(ExcludeRuleContainer.class);
        dependencyConfigurationMappingContainerMock = context.mock(DependencyConfigurationMappingContainer.class);

        excludeRuleWithAllConf = HelperUtil.getTestExcludeRule();
        excludeRuleWithAllConf.addConfiguration(TEST_CONF);
        excludeRuleWithAllConf.addConfiguration(TEST_CONF_2);
        excludeRuleWithAllConf.addConfiguration(TEST_CONF_3);
        excludeRuleWithConf = HelperUtil.getTestExcludeRule();
        excludeRuleWithConf.addConfiguration(TEST_CONF_2);
        excludeRuleWithConf.addConfiguration(TEST_CONF_3);
        testDependencyConfigurations = new HashMap() {
            {
                put(TEST_CONF, WrapUtil.toList("depconf1", "depconf2"));
                put(TEST_CONF_2, WrapUtil.toList("depconf3"));
                put("*", WrapUtil.toList(WILDCARD_CONF));
            }
        };
        context.checking(new Expectations() {
            {
                allowing(excludeRuleContainerMock).createRules(new ArrayList<String>(TEST_CONFS));
                will(returnValue(WrapUtil.toList(excludeRuleWithConf, excludeRuleWithAllConf)));
                allowing(dependencyConfigurationMappingContainerMock).getMappings();
                will(returnValue(testDependencyConfigurations));
            }
        });

        artifact = new Artifact("name", "type", "ext", null, null);
        artifactWithClassifierAndConfs = new Artifact("name2", "type2", "ext2", "classifier2", "http://www.url2.com");
        artifactWithClassifierAndConfs.setConfs(WrapUtil.toList(TEST_CONF_2, TEST_CONF_3));
    }

    @Test
    public void testCreateFromProjectDependency() {
        final ProjectInternal projectMock = context.mock(ProjectInternal.class, "project");
        final ProjectInternal dependencyProjectMock = context.mock(ProjectInternal.class, "dependencyProject");
        final DependencyManager dependencyProjectDependencyManagerMock = context.mock(DependencyManager.class);
        final ModuleRevisionId testModuleRevisionId = ModuleRevisionId.newInstance("org", "name", "1.0", new HashMap());
        context.checking(new Expectations() {{
            allowing(dependencyProjectMock).getDependencies(); will(returnValue(dependencyProjectDependencyManagerMock));
            allowing(dependencyProjectDependencyManagerMock).createModuleRevisionId(); will(returnValue(testModuleRevisionId));
        }});
        DefaultProjectDependency projectDependency = new DefaultProjectDependency(TEST_CONF_MAPPING, dependencyProjectMock, projectMock).
                setTransitive(true);
        projectDependency.setExcludeRules(excludeRuleContainerMock);
        projectDependency.setDependencyConfigurationMappings(dependencyConfigurationMappingContainerMock);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor)
                dependencyDescriptorFactory.createFromProjectDependency(TEST_PARENT, projectDependency);
        assertSame(TEST_PARENT.getModuleRevisionId(), dependencyDescriptor.getParentRevisionId());
        assertEquals(testModuleRevisionId,
                dependencyDescriptor.getDependencyRevisionId());
        assertEquals(projectDependency.isTransitive(), dependencyDescriptor.isTransitive());
        assertTrue(dependencyDescriptor.isChanging());
        assertFalse(dependencyDescriptor.isForce());
        checkDependencyConfigurations(dependencyDescriptor, projectDependency, WILDCARD_CONF);
        checkExcludeRules(dependencyDescriptor, excludeRuleWithAllConf, excludeRuleWithConf);
    }

    @Test
    public void testCreateFromClientModule() {
        ClientModule moduleDependency = new ClientModule(new DependencyFactory(new HashSet()), TEST_CONF_MAPPING, "org.gradle:gradle-core:1.0", new HashMap()).
                setForce(true).
                addArtifact(artifact).
                addArtifact(artifactWithClassifierAndConfs);
        moduleDependency.setExcludeRules(excludeRuleContainerMock);
        moduleDependency.setDependencyConfigurationMappings(dependencyConfigurationMappingContainerMock);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor)
                dependencyDescriptorFactory.createFromClientModule(TEST_PARENT, moduleDependency);
        assertSame(TEST_PARENT.getModuleRevisionId(), dependencyDescriptor.getParentRevisionId());
        checkModuleDependency(dependencyDescriptor, moduleDependency);
    }

    private void checkModuleDependency(DefaultDependencyDescriptor dependencyDescriptor, ClientModule dependency) {
        assertEquals(ModuleRevisionId.newInstance(dependency.getGroup(), dependency.getName(), dependency.getVersion(),
                WrapUtil.toMap(ClientModule.CLIENT_MODULE_KEY, dependency.getId())),
                dependencyDescriptor.getDependencyRevisionId());
        assertEquals(dependency.isTransitive(), dependencyDescriptor.isTransitive());
        assertFalse(dependencyDescriptor.isChanging());
        assertEquals(dependency.isForce(), dependencyDescriptor.isForce());
        checkDependencyConfigurations(dependencyDescriptor, dependency, WILDCARD_CONF);
        checkExcludeRules(dependencyDescriptor, excludeRuleWithAllConf, excludeRuleWithConf);
        checkArtifacts(dependencyDescriptor, artifact, artifactWithClassifierAndConfs);
    }

    @Test
    public void testCreateFromModuleDependency() {
        DefaultModuleDependency moduleDependency = new DefaultModuleDependency(TEST_CONF_MAPPING, "org.gradle:gradle-core:1.0").
                setChanging(true).
                setForce(true).
                addArtifact(artifact).
                addArtifact(artifactWithClassifierAndConfs);
        moduleDependency.setExcludeRules(excludeRuleContainerMock);
        moduleDependency.setDependencyConfigurationMappings(dependencyConfigurationMappingContainerMock);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor)
                dependencyDescriptorFactory.createFromModuleDependency(TEST_PARENT, moduleDependency);
        assertSame(TEST_PARENT.getModuleRevisionId(), dependencyDescriptor.getParentRevisionId());
        checkModuleDependency(dependencyDescriptor, moduleDependency);
    }

    private void checkModuleDependency(DefaultDependencyDescriptor dependencyDescriptor, DefaultModuleDependency dependency) {
        assertEquals(ModuleRevisionId.newInstance(dependency.getGroup(), dependency.getName(), dependency.getVersion(), new HashMap()),
                dependencyDescriptor.getDependencyRevisionId());
        assertEquals(dependency.isTransitive(), dependencyDescriptor.isTransitive());
        assertEquals(dependency.isChanging(), dependencyDescriptor.isChanging());
        assertEquals(dependency.isForce(), dependencyDescriptor.isForce());
        checkDependencyConfigurations(dependencyDescriptor, dependency, WILDCARD_CONF);
        checkExcludeRules(dependencyDescriptor, excludeRuleWithAllConf, excludeRuleWithConf);
        checkArtifacts(dependencyDescriptor, artifact, artifactWithClassifierAndConfs);
    }

    private void checkArtifacts(DefaultDependencyDescriptor dependencyDescriptor, Artifact artifact, Artifact artifactWithClassifierAndConfs) {
        DependencyArtifactDescriptor artifactDescriptor = dependencyDescriptor.getDependencyArtifacts(TEST_CONF)[0];
        assertTrue(Arrays.asList(dependencyDescriptor.getDependencyArtifacts(TEST_CONF_2)).contains(artifactDescriptor));
        assertTrue(Arrays.asList(dependencyDescriptor.getDependencyArtifacts(TEST_CONF_3)).contains(artifactDescriptor));
        assertEquals(new HashMap(), artifactDescriptor.getExtraAttributes());
        assertEquals(null, artifactDescriptor.getUrl());
        compareArtifacts(artifact, artifactDescriptor);

        DependencyArtifactDescriptor artifactDescriptorWithClassifierAndConfs = dependencyDescriptor.getDependencyArtifacts(TEST_CONF_2)[0];
        if (artifactDescriptorWithClassifierAndConfs == artifactDescriptor) {
            artifactDescriptorWithClassifierAndConfs = dependencyDescriptor.getDependencyArtifacts(TEST_CONF_2)[1];
        }
        assertTrue(Arrays.asList(dependencyDescriptor.getDependencyArtifacts(TEST_CONF_3)).contains(artifactDescriptorWithClassifierAndConfs));
        assertEquals(WrapUtil.toMap(DependencyManager.CLASSIFIER, artifactWithClassifierAndConfs.getClassifier()), artifactDescriptorWithClassifierAndConfs.getExtraAttributes());
        compareArtifacts(artifactWithClassifierAndConfs, artifactDescriptorWithClassifierAndConfs);
        try {
            assertEquals(new URL(artifactWithClassifierAndConfs.getUrl()), artifactDescriptorWithClassifierAndConfs.getUrl());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private void compareArtifacts(Artifact artifact, DependencyArtifactDescriptor artifactDescriptor) {
        assertEquals(artifact.getName(), artifactDescriptor.getName());
        assertEquals(artifact.getType(), artifactDescriptor.getType());
        assertEquals(artifact.getExtension(), artifactDescriptor.getExt());
    }

    private void checkExcludeRules(DefaultDependencyDescriptor dependencyDescriptor, ExcludeRule excludeRuleWithNoConf, ExcludeRule excludeRuleWithConf) {
        ExcludeRule descriptorExcludeRule = dependencyDescriptor.getExcludeRules(TEST_CONF)[0];
        assertSame(excludeRuleWithNoConf, descriptorExcludeRule);
        assertTrue(Arrays.asList(dependencyDescriptor.getExcludeRules(TEST_CONF_2)).contains(descriptorExcludeRule));
        assertTrue(Arrays.asList(dependencyDescriptor.getExcludeRules(TEST_CONF_3)).contains(descriptorExcludeRule));

        ExcludeRule descriptorExcludeRuleWithConf = dependencyDescriptor.getExcludeRules(TEST_CONF_2)[0];
        if (descriptorExcludeRuleWithConf == descriptorExcludeRule) {
            descriptorExcludeRuleWithConf = dependencyDescriptor.getExcludeRules(TEST_CONF_2)[1];
        }
        assertSame(excludeRuleWithConf, descriptorExcludeRuleWithConf);
        assertTrue(Arrays.asList(dependencyDescriptor.getExcludeRules(TEST_CONF_3)).contains(descriptorExcludeRuleWithConf));
    }


    private void checkDependencyConfigurations(DefaultDependencyDescriptor dependencyDescriptor, Dependency dependency, String wildcardConf) {
        for (String masterConf : dependency.getDependencyConfigurationMappings().getMappings().keySet()) {
            if (!masterConf.equals("*")) {
                assertEquals(GUtil.addLists(dependency.getDependencyConfigurationMappings().getMappings().get(masterConf), WrapUtil.toList(wildcardConf)),
                        Arrays.asList(dependencyDescriptor.getDependencyConfigurations(masterConf)));
            }
        }
    }
}

