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
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultIvyDependencyPublisherTest {
    JUnit4Mockery context = new JUnit4Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
    }};
    
    private ModuleDescriptor moduleDescriptorDummy = context.mock(ModuleDescriptor.class);
    private PublishOptionsFactory publishOptionsFactoryStub = context.mock(PublishOptionsFactory.class);
    private PublishEngine publishEngineMock = context.mock(PublishEngine.class);
    private List<DependencyResolver> expectedResolverList = WrapUtil.toList(context.mock(DependencyResolver.class));
    private DefaultIvyDependencyPublisher ivyDependencyPublisher = new DefaultIvyDependencyPublisher(publishOptionsFactoryStub);
    private String expectedConf = "conf1";
    private PublishOptions expectedPublishOptions = new PublishOptions();
    private File someDescriptorDestination = new File("somePath");

    @Test
    public void testPublishWithUploadModuleDescriptorFalse() throws IOException {
        context.checking(new Expectations() {{
                allowing(publishOptionsFactoryStub).createPublishOptions(WrapUtil.toSet(expectedConf), someDescriptorDestination);
                will(returnValue(expectedPublishOptions));

                one(publishEngineMock).publish(
                        moduleDescriptorDummy,
                        DefaultIvyDependencyPublisher.ARTIFACT_PATTERN,
                        expectedResolverList.get(0),
                        expectedPublishOptions);
        }});

        ivyDependencyPublisher.publish(WrapUtil.toSet(expectedConf), expectedResolverList, moduleDescriptorDummy, someDescriptorDestination, publishEngineMock);
    }

}
