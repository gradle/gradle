/*
 * Copyright 2009 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Date;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultModuleDescriptorForUploadConverterTest {
    @org.junit.Test
    public void testCreateModuleDescriptor() {
        DefaultModuleDescriptor sourceModuleDescriptor = new DefaultModuleDescriptor(
                ModuleRevisionId.newInstance("org", "name", "version"),
                "status",
                new Date());
        sourceModuleDescriptor.addDependency(new DefaultDependencyDescriptor(
            ModuleRevisionId.newInstance("deporg", "depname", "depversion"), true        
        ));
        String confName = "someConf";
        sourceModuleDescriptor.addConfiguration(new Configuration(confName));
        Map noFilePathExtraAttributes = WrapUtil.toMap("someKey", "someValue");
        sourceModuleDescriptor.addArtifact(confName, new DefaultArtifact(
                sourceModuleDescriptor.getModuleRevisionId(),
                new Date(),
                "someName",
                "someType",
                "someExt",
                GUtil.addMaps(noFilePathExtraAttributes,
                        WrapUtil.toMap(DefaultIvyDependencyPublisher.FILE_PATH_EXTRA_ATTRIBUTE, "somePath"))));

        ModuleDescriptor convertedModuleDescriptor = new DefaultModuleDescriptorForUploadConverter().createModuleDescriptor(sourceModuleDescriptor);

        assertThat(convertedModuleDescriptor.getModuleRevisionId(), equalTo(sourceModuleDescriptor.getModuleRevisionId()));
        assertThat(convertedModuleDescriptor.getStatus(), equalTo(sourceModuleDescriptor.getStatus()));
        assertThat(convertedModuleDescriptor.getPublicationDate(), equalTo(sourceModuleDescriptor.getPublicationDate()));
        assertThat(convertedModuleDescriptor.getConfigurations(), equalTo(sourceModuleDescriptor.getConfigurations()));
        assertThat(convertedModuleDescriptor.getDependencies(), equalTo(sourceModuleDescriptor.getDependencies()));
        assertThatArtifactsAreEqualExceptFilePathAttribute(sourceModuleDescriptor.getArtifacts(confName)[0],
                convertedModuleDescriptor.getArtifacts(confName)[0], noFilePathExtraAttributes);
    }

    private void assertThatArtifactsAreEqualExceptFilePathAttribute(Artifact sourceArtifact, Artifact convertedArtifact, Map noFilePathExtraAttributes) {
        assertThat(convertedArtifact.getModuleRevisionId(), equalTo(sourceArtifact.getModuleRevisionId()));
        assertThat(convertedArtifact.getPublicationDate(), equalTo(sourceArtifact.getPublicationDate()));
        assertThat(convertedArtifact.getName(), equalTo(sourceArtifact.getName()));
        assertThat(convertedArtifact.getType(), equalTo(sourceArtifact.getType()));
        assertThat(convertedArtifact.getExt(), equalTo(sourceArtifact.getExt()));
        assertThat(convertedArtifact.getExtraAttributes(), equalTo(noFilePathExtraAttributes));
    }
}
