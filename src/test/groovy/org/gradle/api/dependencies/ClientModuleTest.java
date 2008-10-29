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

package org.gradle.api.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.dependencies.AbstractDependencyContainerTest;
import org.gradle.api.internal.dependencies.DefaultDependencyContainer;
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.dependencies.DefaultModuleDependency;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class ClientModuleTest extends AbstractDependencyContainerTest {
    static final String TEST_GROUP = "org.gradle";
    static final String TEST_NAME = "gradle-core";
    static final String TEST_VERSION = "4.4-beta2";
    static final String TEST_CLASSIFIER = "jdk-1.4";
    static final String TEST_MODULE_DESCRIPTOR = String.format("%s:%s:%s", TEST_GROUP, TEST_NAME, TEST_VERSION);
    static final String TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + ":" + TEST_CLASSIFIER;
    
    ClientModule clientModule;

    Map testModuleRegistry = WrapUtil.toMap("a", "a");

    Set parentConfs = WrapUtil.toSet("parentConf");

    DependencyDescriptorFactory dependencyDescriptorFactoryMock;

    DefaultDependencyDescriptor expectedDependencyDescriptor;

    public DefaultDependencyContainer getTestObj() {
        return clientModule;
    }

    @Before public void setUp() {
        super.setUp();
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory.class);
        clientModule = new ClientModule(dependencyFactory, parentConfs, TEST_MODULE_DESCRIPTOR, testModuleRegistry);
        clientModule.setProject(project);
        testDefaultConfs = clientModule.getDefaultConfs();
        testConfs = clientModule.getDefaultConfs();
        clientModule.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock);
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor();
    }

    @Test public void testInitWithoutClassifier() {
        checkInit(TEST_MODULE_DESCRIPTOR);
    }

    @Test public void testInitWitClassifier() {
        clientModule = new ClientModule(dependencyFactory, parentConfs, TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER, testModuleRegistry);
        checkInit(TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER);
        Artifact artifact = clientModule.getArtifacts().get(0);
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(Artifact.DEFAULT_TYPE, artifact.getType());
        assertEquals(Artifact.DEFAULT_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    private void checkInit(String id) {
        assertEquals(clientModule.getDefaultConfs(), WrapUtil.toList(Dependency.DEFAULT_CONFIGURATION));
        assertEquals(clientModule.getDependencyConfigurationMappings().getMasterConfigurations(), parentConfs);
        assertEquals(clientModule.getDefaultConfs(), WrapUtil.toList(Dependency.DEFAULT_CONFIGURATION));
        assertEquals(clientModule.getId(), id);
        assertEquals(clientModule.getClientModuleRegistry(), testModuleRegistry);
        assertEquals(TEST_GROUP, clientModule.getGroup());
        assertEquals(TEST_NAME, clientModule.getName());
        assertEquals(TEST_VERSION, clientModule.getVersion());
        assertFalse(clientModule.isForce());
        assertTrue(clientModule.isTransitive());
    }

    @Test (expected = InvalidUserDataException.class)
    public void testInitWithNull() {
        new ClientModule(dependencyFactory, parentConfs, null, testModuleRegistry);
    }

    @Test (expected = InvalidUserDataException.class)
    public void testInitWithFiveParts() {
        new ClientModule(dependencyFactory, parentConfs, "1:2:3:4:5", testModuleRegistry);
    }

    @Test (expected = InvalidUserDataException.class)
    public void testInitWithTwoParts() {
        new ClientModule(dependencyFactory, parentConfs, "1:2", testModuleRegistry);
    }

    @Test (expected = InvalidUserDataException.class)
    public void testInitWithOneParts() {
        new ClientModule(dependencyFactory, parentConfs, "1", testModuleRegistry);
    }


    @Test public void testCreateDependencyDescriptor() {
        clientModule = new ClientModule(dependencyFactory, parentConfs, TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER, testModuleRegistry);
        clientModule.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock);
        final ModuleDescriptor parentModuleDescriptorMock = context.mock(ModuleDescriptor.class);
        context.checking(new Expectations() {{
            one(dependencyDescriptorFactoryMock).createFromClientModule(parentModuleDescriptorMock,
                    clientModule);
            will(returnValue(expectedDependencyDescriptor));
        }});
        assertSame(expectedDependencyDescriptor, clientModule.createDependencyDescriptor(parentModuleDescriptorMock));
    }

    @Test (expected = UnsupportedOperationException.class)
    public void testUnsupportedOperationsClientModule() {
        clientModule.clientModule(WrapUtil.toList("a"), "a");
    }

    @Test (expected = UnsupportedOperationException.class)
    public void testUnsupportedOperationsDependencies() {
        clientModule.dependencies(WrapUtil.toList("a"), "a");
    }

    @Test (expected = UnsupportedOperationException.class)
    public void testUnsupportedOperationsDependency() {
        clientModule.dependency(WrapUtil.toList("a"), "a");
    }


}
