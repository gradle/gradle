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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.publish.PublishEngine;
import org.apache.ivy.core.publish.PublishOptions;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.PublishInstruction;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultIvyDependencyPublisherTest {
    private static final String TEST_DEFAULT_PATTERN = "defaultPattern";
    private static final String TEST_MAVEN_PACKAGING = "somePackging";

    private DefaultIvyDependencyPublisher ivyDependencyPublisher;
    private PublishEngine publishEngineMock;
    private String expectedConf;
    private PublishOptionsFactory publishOptionsFactoryMock;
    private PublishOptions expectedPublishOptions;

    private File expectedIvyFile;
    JUnit4Mockery context = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    private ModuleDescriptor moduleDescriptorMock;
    private List<DependencyResolver> expectedResolverList;

    @Before
    public void setUp() {
        publishOptionsFactoryMock = context.mock(PublishOptionsFactory.class);
        expectedPublishOptions = new PublishOptions();
        publishEngineMock = context.mock(PublishEngine.class);
        ivyDependencyPublisher = new DefaultIvyDependencyPublisher(publishOptionsFactoryMock);
        expectedConf = "conf1";
        moduleDescriptorMock = context.mock(ModuleDescriptor.class);
        expectedIvyFile = new File(HelperUtil.makeNewTestDir(), "ivy.xml");
        expectedResolverList = WrapUtil.toList(context.mock(DependencyResolver.class));
    }

    @Test
    public void testPublishWithUploadModuleDescriptorTrue() throws IOException, ParseException {
        final PublishInstruction publishInstruction = new PublishInstruction();
        publishInstruction.setIvyFileParentDir(expectedIvyFile.getParentFile());
        context.checking(new Expectations() {
            {
                allowing(publishOptionsFactoryMock).createPublishOptions(WrapUtil.toSet(expectedConf), publishInstruction, expectedIvyFile);
                will(returnValue(expectedPublishOptions));
                
                one(publishEngineMock).publish(moduleDescriptorMock,
                        DefaultIvyDependencyPublisher.ARTIFACT_PATTERN,
                        expectedResolverList.get(0),
                        expectedPublishOptions);
                one(moduleDescriptorMock).toIvyFile(expectedIvyFile);
            }
        });

        ivyDependencyPublisher.publish(WrapUtil.toSet(expectedConf), publishInstruction, expectedResolverList, moduleDescriptorMock, publishEngineMock);
    }

    @Test
    public void testPublishWithUploadModuleDescriptorFalse() throws IOException {
        final PublishInstruction publishInstruction = new PublishInstruction();
        context.checking(new Expectations() {
            {
                allowing(publishOptionsFactoryMock).createPublishOptions(WrapUtil.toSet(expectedConf), publishInstruction, null);
                will(returnValue(expectedPublishOptions));
                
                one(publishEngineMock).publish(
                        moduleDescriptorMock,
                        DefaultIvyDependencyPublisher.ARTIFACT_PATTERN,
                        expectedResolverList.get(0),
                        expectedPublishOptions);
            }
        });


        ivyDependencyPublisher.publish(WrapUtil.toSet(expectedConf), publishInstruction, expectedResolverList, moduleDescriptorMock, publishEngineMock);
    }

}
