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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.publish.PublishEngine;
import org.apache.ivy.core.publish.PublishOptions;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.dependencies.ResolverContainer;
import org.gradle.api.dependencies.MavenPomGenerator;
import org.gradle.api.DependencyManager;
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
import java.util.List;
import java.text.ParseException;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyPublisherTest {
    private static final String TEST_DEFAULT_PATTERN = "defaultPattern";
    private static final String TEST_MAVEN_PACKAGING = "somePackging";

    private DefaultDependencyPublisher dependencyPublisher;
    private PublishEngine publishEngineMock;
    private DependencyManager dependencyManagerMock;
    private MavenPomGenerator mavenMockPomGenerator;
    private List<String> expectedConfs;
    private ResolverContainer resolverContainerMock;

    private File expectedIvyFile;
    JUnit4Mockery context = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    private ModuleDescriptor moduleDescriptorMock;
    private List<String> expectedSrcArtifactPatterns;
    private File expectedPomFile;
    private FileSystemResolver testPomDescriptorResolver;
    private FileSystemResolver testIvyDescriptorResolver;

    @Before
    public void setUp() {
        publishEngineMock = context.mock(PublishEngine.class);
        prepareResolverContainerMock();
        dependencyPublisher = new DefaultDependencyPublisher();
        prepareDependencyManagerMock();
        expectedConfs = WrapUtil.toList("conf1");
        moduleDescriptorMock = context.mock(ModuleDescriptor.class);
        expectedIvyFile = new File(HelperUtil.makeNewTestDir(), "ivy.xml");
        expectedPomFile = new File(HelperUtil.makeNewTestDir(), "pom.xml");
        expectedSrcArtifactPatterns = new ArrayList<String>();
        expectedSrcArtifactPatterns.addAll(dependencyManagerMock.getAbsoluteArtifactPatterns());
        expectedSrcArtifactPatterns.add(new File("a", TEST_DEFAULT_PATTERN).getAbsolutePath());
        expectedSrcArtifactPatterns.add(new File("b", TEST_DEFAULT_PATTERN).getAbsolutePath());
    }

    private void prepareDependencyManagerMock() {
        dependencyManagerMock = context.mock(DependencyManager.class);
        mavenMockPomGenerator = context.mock(MavenPomGenerator.class);
        context.checking(new Expectations() {
            {
                allowing(dependencyManagerMock).getAbsoluteArtifactPatterns();
                will(returnValue(WrapUtil.toList("absolutePattern")));
                allowing(dependencyManagerMock).getArtifactParentDirs();
                will(returnValue(WrapUtil.toSet(new File("a"), new File("b"))));
                allowing(dependencyManagerMock).getDefaultArtifactPattern();
                will(returnValue(TEST_DEFAULT_PATTERN));
                allowing(dependencyManagerMock).getMaven();
                will(returnValue(mavenMockPomGenerator));
                allowing(mavenMockPomGenerator).getPackaging();
                will(returnValue(TEST_MAVEN_PACKAGING));
            }
        });
    }

    private void prepareResolverContainerMock() {
        resolverContainerMock = context.mock(ResolverContainer.class);
        testIvyDescriptorResolver = new FileSystemResolver();
        testIvyDescriptorResolver.setName("ivy");
        testPomDescriptorResolver = new FileSystemResolver();
        testPomDescriptorResolver.setName("pom");
        final List<FileSystemResolver> expectedResolverList = WrapUtil.toList(testIvyDescriptorResolver, testPomDescriptorResolver);
        context.checking(new Expectations() {
            {
                allowing(resolverContainerMock).getResolverList();
                will(returnValue(expectedResolverList));
                allowing(resolverContainerMock).isPomResolver(testIvyDescriptorResolver);
                will(returnValue(false));
                allowing(resolverContainerMock).isPomResolver(testPomDescriptorResolver);
                will(returnValue(true));
                allowing(resolverContainerMock).hasIvyResolvers();
                will(returnValue(true));
                allowing(resolverContainerMock).hasPomResolvers();
                will(returnValue(true));
            }
        });
    }

    @Test
    public void testPublishWithUploadModuleDescriptorTrue() throws IOException, ParseException {
        context.checking(new Expectations() {
            {
                one(publishEngineMock).publish(
                        with(same(moduleDescriptorMock)),
                        with(equal(expectedSrcArtifactPatterns)),
                        with(equal(testIvyDescriptorResolver)),
                        with(reflectionEquals(createPublishOptions(expectedIvyFile))));
                one(publishEngineMock).publish(
                        with(same(moduleDescriptorMock)),
                        with(equal(expectedSrcArtifactPatterns)),
                        with(equal(testPomDescriptorResolver)),
                        with(reflectionEquals(createPublishOptions(expectedPomFile))));
                one(moduleDescriptorMock).toIvyFile(expectedIvyFile);
                one(mavenMockPomGenerator).toPomFile(moduleDescriptorMock, expectedPomFile);
            }
        });
        dependencyPublisher.publish(expectedConfs, resolverContainerMock, moduleDescriptorMock,
                true, expectedIvyFile.getParentFile(), dependencyManagerMock, publishEngineMock);
    }

    private PublishOptions createPublishOptions(File descriptorFile) {
        final PublishOptions publishOptions = new PublishOptions();
        publishOptions.setConfs(expectedConfs.toArray(new String[expectedConfs.size()]));
        publishOptions.setOverwrite(true);
        publishOptions.setSrcIvyPattern(descriptorFile.getAbsolutePath());
        return publishOptions;
    }

    @Test
    public void testPublishWithUploadModuleDescriptorFalse() throws IOException {
        final PublishOptions publishOptions = new PublishOptions();
        publishOptions.setConfs(expectedConfs.toArray(new String[expectedConfs.size()]));
        publishOptions.setOverwrite(true);
        context.checking(new Expectations() {
            {
                one(publishEngineMock).publish(
                        with(same(moduleDescriptorMock)),
                        with(equal(expectedSrcArtifactPatterns)),
                        with(equal(testIvyDescriptorResolver)),
                        with(reflectionEquals(publishOptions)));
                one(publishEngineMock).publish(
                        with(same(moduleDescriptorMock)),
                        with(equal(expectedSrcArtifactPatterns)),
                        with(equal(testPomDescriptorResolver)),
                        with(reflectionEquals(publishOptions)));
            }
        });
        dependencyPublisher.publish(expectedConfs, resolverContainerMock, moduleDescriptorMock,
                false, expectedIvyFile.getParentFile(), dependencyManagerMock, publishEngineMock);
    }

}
