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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.publish.PublishEngine;
import org.apache.ivy.core.publish.PublishOptions;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.dependencies.ResolverContainer;
import org.gradle.util.HelperUtil;
import static org.gradle.util.ReflectionEqualsMatcher.reflectionEquals;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyPublisherTest {
    private static final String TEST_DEFAULT_PATTERN = "defaultPattern";
    
    private DefaultDependencyPublisher dependencyPublisher;
    private PublishEngine publishEngineMock;
    private DefaultDependencyManager dependencyManager;
    private List<String> expectedConfs;
    private ResolverContainer expectedResolverContainer;

    private File expectedIvyFile;
    JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private DefaultModuleDescriptor expectedModuleDescriptor;
    private List<String> expectedSrcArtifactPatterns;

    @Before
    public void setUp() {
        publishEngineMock = context.mock(PublishEngine.class);
        dependencyPublisher = new DefaultDependencyPublisher();
        dependencyManager = new DefaultDependencyManager();
        dependencyManager.setAbsoluteArtifactPatterns(WrapUtil.toList("absolutePattern"));
        dependencyManager.setArtifactParentDirs(WrapUtil.toSet(new File("a"), new File("b")));
        dependencyManager.setDefaultArtifactPattern(TEST_DEFAULT_PATTERN);
        expectedConfs = WrapUtil.toList("conf1");
        expectedResolverContainer = new ResolverContainer(null);
        expectedResolverContainer.add(new FileSystemResolver(), null);
        expectedModuleDescriptor = DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance("org", "name", "1.0", new HashMap()));
        expectedIvyFile = new File(HelperUtil.makeNewTestDir(), "ivy.xml");
        expectedSrcArtifactPatterns = new ArrayList<String>();
        expectedSrcArtifactPatterns.addAll(dependencyManager.getAbsoluteArtifactPatterns());
        expectedSrcArtifactPatterns.add(new File("a", TEST_DEFAULT_PATTERN).getAbsolutePath());
        expectedSrcArtifactPatterns.add(new File("b", TEST_DEFAULT_PATTERN).getAbsolutePath());
    }

    @Test
    public void testPublishWithUploadModuleDescriptorTrue() throws IOException {
        final PublishOptions publishOptions = new PublishOptions();
        publishOptions.setConfs(expectedConfs.toArray(new String[expectedConfs.size()]));
        publishOptions.setOverwrite(true);
        publishOptions.setSrcIvyPattern(expectedIvyFile.getAbsolutePath());
        context.checking(new Expectations() {{
          one(publishEngineMock).publish(
                  with(equal(expectedModuleDescriptor)),
                  with(equal(expectedSrcArtifactPatterns)),
                  (DependencyResolver) with(equal(expectedResolverContainer.getResolverList().get(0))),
                  with(reflectionEquals(publishOptions)));
        }});
        dependencyPublisher.publish(expectedConfs, expectedResolverContainer, expectedModuleDescriptor,
                true, expectedIvyFile, dependencyManager, publishEngineMock);
    }

    @Test
    public void testPublishWithUploadModuleDescriptorFalse() throws IOException {
        final PublishOptions publishOptions = new PublishOptions();
        publishOptions.setConfs(expectedConfs.toArray(new String[expectedConfs.size()]));
        publishOptions.setOverwrite(true);
        context.checking(new Expectations() {{
          one(publishEngineMock).publish(
                  with(equal(expectedModuleDescriptor)),
                  with(equal(expectedSrcArtifactPatterns)),
                  (DependencyResolver) with(equal(expectedResolverContainer.getResolverList().get(0))),
                  with(reflectionEquals(publishOptions)));
        }});
        dependencyPublisher.publish(expectedConfs, expectedResolverContainer, expectedModuleDescriptor,
                false, expectedIvyFile, dependencyManager, publishEngineMock);
    }

}
