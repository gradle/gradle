/*
 * Copyright 2007-2009 the original author or authors.
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

import org.gradle.api.specs.Specs;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class PublishInstructionTest {
    private PublishInstruction publishInstruction;

    @Before
    public void setUp() {
        publishInstruction = new PublishInstruction();
    }

    protected PublishInstruction getInstruction() {
        return publishInstruction;
    }

    @Test
    public void init() {
        assertThat(getInstruction().getModuleDescriptor().isPublish(), equalTo(true));
        assertThat(getInstruction().getModuleDescriptor().getIvyFileParentDir(), equalTo(null));
        assertThat(getInstruction().getModuleDescriptor().getConfigurationSpec(), equalTo(Specs.SATISFIES_ALL));
        assertThat(getInstruction().getModuleDescriptor().getDependencySpec(), equalTo(Specs.SATISFIES_ALL));
        assertThat(getInstruction().getArtifactSpec(), equalTo(Specs.SATISFIES_ALL));
    }
}
