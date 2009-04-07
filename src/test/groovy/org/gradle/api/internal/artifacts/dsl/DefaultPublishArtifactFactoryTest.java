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
package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.artifacts.PublishArtifact;
import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.hamcrest.Matchers;

import java.awt.*;

/**
 * @author Hans Dockter
 */
public class DefaultPublishArtifactFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    
    private DefaultPublishArtifactFactory publishArtifactFactory = new DefaultPublishArtifactFactory();

    @Test
    public void createArtifact() {
        AbstractArchiveTask archiveTaskMock = context.mock(AbstractArchiveTask.class);
        ArchivePublishArtifact publishArtifact = (ArchivePublishArtifact) publishArtifactFactory.createArtifact(archiveTaskMock);
        assertThat(publishArtifact.getArchiveTask(), Matchers.sameInstance(archiveTaskMock));
    }

    @Test(expected = InvalidUserDataException.class)
    public void createArtifactWithNullNotation_shouldThrowInvalidUserDataEx() {
        publishArtifactFactory.createArtifact(null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void createArtifactWithUnknownNotation_shouldThrowInvalidUserDataEx() {
        publishArtifactFactory.createArtifact(new Point(1,2));
    }
}
