/*
 * Copyright 2007-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package org.gradle.api.internal.artifacts.publish


import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.util.WrapUtil
import org.jmock.Expectations
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat

/**
 * @author Hans Dockter
 */
public class ArchivePublishArtifactTest extends AbstractPublishArtifactTest {
    private AbstractArchiveTask archiveTask = context.mock(AbstractArchiveTask.class)

    @Override
    protected PublishArtifact createPublishArtifact(final String classifier) {
        prepareMocks(classifier, "")
        return new ArchivePublishArtifact(archiveTask)
    }

    private void prepareMocks(final String classifier, final String appendix) {
        context.checking(new Expectations() {
            {
                allowing(archiveTask).getExtension()
                will(returnValue(getTestExt()))

                allowing(archiveTask).getBaseName()
                will(returnValue(getTestName()))

                allowing(archiveTask).getAppendix()
                will(returnValue(appendix))

                allowing(archiveTask).getArchivePath()
                will(returnValue(getTestFile()))

                allowing(archiveTask).getClassifier()
                will(returnValue(classifier))
            }
        })
    }

    @Override
    protected String getTestType() {
        return getTestExt()
    }

    @Test
    public void init() {
        ArchivePublishArtifact publishArtifact = (ArchivePublishArtifact) createPublishArtifact(getTestClassifier())
        assertThat((Set<AbstractArchiveTask>) publishArtifact.getBuildDependencies().getDependencies(null), equalTo(WrapUtil.toSet(archiveTask)))
        assertCommonPropertiesAreSet(publishArtifact, true)
        assertThat(publishArtifact.getArchiveTask(), sameInstance(archiveTask))
    }

    @Test
    public void nameWithAppendix() {
        String testAppendix = "appendix"
        prepareMocks(getTestClassifier(), testAppendix)
        PublishArtifact publishArtifact = new ArchivePublishArtifact(archiveTask)
        assertThat(publishArtifact.getName(), equalTo(getTestName() + "-" + testAppendix))
    }

    @Test
    public void equality() {
        String testAppendix = "appendix"
        prepareMocks(getTestClassifier(), testAppendix)
        ArchivePublishArtifact publishArtifact1 = new ArchivePublishArtifact(archiveTask)
        ArchivePublishArtifact publishArtifact2 = new ArchivePublishArtifact(archiveTask)

        assert publishArtifact1 == publishArtifact2
        publishArtifact1.name = "changed"
        assert publishArtifact1 != publishArtifact2
    }
}
