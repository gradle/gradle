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
import org.gradle.api.DependencyManager;
import org.gradle.api.internal.dependencies.*;
import org.gradle.api.internal.dependencies.ivy.DependencyDescriptorFactory;
import org.gradle.api.internal.dependencies.ivy.ClientModuleDescriptorFactory;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.hamcrest.Matchers;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class ClientModuleTest {
    static final String TEST_GROUP = "org.gradle";
    static final String TEST_NAME = "gradle-core";
    static final String TEST_VERSION = "4.4-beta2";
    static final String TEST_CLASSIFIER = "jdk-1.4";
    static final String TEST_MODULE_DESCRIPTOR = String.format("%s:%s:%s", TEST_GROUP, TEST_NAME, TEST_VERSION);
    static final String TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + ":" + TEST_CLASSIFIER;

    protected static final DefaultDependencyConfigurationMappingContainer TEST_CONF_MAPPING =
            new DefaultDependencyConfigurationMappingContainer() {{
                addMasters(new DefaultConfiguration("someConf", new DefaultConfigurationContainer()));
            }};

    ClientModule clientModule;

    Map<String, ModuleDescriptor> testModuleRegistry = new HashMap<String, ModuleDescriptor>();

    DependencyContainerInternal dependencyContainerMock;

    DefaultDependencyDescriptor expectedDependencyDescriptor;

    private JUnit4Mockery context = new JUnit4Mockery();
    private DependencyDescriptorFactory dependencyDescriptorFactoryMock;


    @Before
    public void setUp() {
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory.class);
        dependencyContainerMock = context.mock(DependencyContainerInternal.class);
        clientModule = new ClientModule(TEST_CONF_MAPPING, TEST_MODULE_DESCRIPTOR, dependencyContainerMock);
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor();
    }

    @Test
    public void testInitWithoutClassifier() {
        checkInit(TEST_MODULE_DESCRIPTOR);
    }

    @Test
    public void testInitWitClassifier() {
        clientModule = new ClientModule(TEST_CONF_MAPPING, TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER, dependencyContainerMock);
        checkInit(TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER);
        Artifact artifact = clientModule.getArtifacts().get(0);
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(Artifact.DEFAULT_TYPE, artifact.getType());
        assertEquals(Artifact.DEFAULT_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    private void checkInit(String id) {
        assertEquals(clientModule.getDependencyConfigurationMappings(), TEST_CONF_MAPPING);
        assertEquals(clientModule.getDependencyConfigurationMappings(), TEST_CONF_MAPPING);
        assertEquals(clientModule.getId(), id);
        assertEquals(TEST_GROUP, clientModule.getGroup());
        assertEquals(TEST_NAME, clientModule.getName());
        assertEquals(TEST_VERSION, clientModule.getVersion());
        assertFalse(clientModule.isForce());
        assertTrue(clientModule.isTransitive());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testInitWithNull() {
        new ClientModule(TEST_CONF_MAPPING, null, dependencyContainerMock);
    }

    @Test(expected = InvalidUserDataException.class)
    public void testInitWithFiveParts() {
        new ClientModule(TEST_CONF_MAPPING, "1:2:3:4:5", dependencyContainerMock);
    }

    @Test(expected = InvalidUserDataException.class)
    public void testInitWithTwoParts() {
        new ClientModule(TEST_CONF_MAPPING, "1:2", dependencyContainerMock);
    }

    @Test(expected = InvalidUserDataException.class)
    public void testInitWithOneParts() {
        new ClientModule(TEST_CONF_MAPPING, "1", dependencyContainerMock);
    }


    @Test
    public void testCreateDependencyDescriptor() {
        final ClientModule clientModule = new ClientModule(TEST_CONF_MAPPING, TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER, dependencyContainerMock);
        final ClientModuleDescriptorFactory clientModuleDescriptorFactoryMock = context.mock(ClientModuleDescriptorFactory.class);
                clientModule.setClientModuleDescriptorFactory(clientModuleDescriptorFactoryMock);
        clientModule.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock);
        
        final ModuleDescriptor parentModuleDescriptorMock = context.mock(ModuleDescriptor.class, "parent");
        final ModuleDescriptor clientModuleDescriptorMock = context.mock(ModuleDescriptor.class, "clientModule");
        context.checking(new Expectations() {{
            allowing(dependencyContainerMock).getClientModuleRegistry();
            will(returnValue(testModuleRegistry));
            
            allowing(dependencyDescriptorFactoryMock).createFromClientModule(parentModuleDescriptorMock,
                    clientModule);
            will(returnValue(expectedDependencyDescriptor));

            allowing(clientModuleDescriptorFactoryMock).createModuleDescriptor(
                    expectedDependencyDescriptor.getDependencyRevisionId(), dependencyContainerMock);
            will(returnValue(clientModuleDescriptorMock));

        }});
        assertSame(expectedDependencyDescriptor, clientModule.createDependencyDescriptor(parentModuleDescriptorMock));
        assertThat((ModuleDescriptor) testModuleRegistry.get(TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER), Matchers.sameInstance(clientModuleDescriptorMock));
    }
}
