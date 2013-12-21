/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.artifacts.publish;

import org.gradle.api.Task;
import org.gradle.api.artifacts.PublishArtifact;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class DefaultPublishArtifactTest extends AbstractPublishArtifactTest {
    protected PublishArtifact createPublishArtifact(String classifier) {
        return new DefaultPublishArtifact(getTestName(), getTestExt(), getTestType(), classifier, getDate(), getTestFile());
    }

    @Test
    public void init() {
        Task task1 = context.mock(Task.class, "task1");
        Task task2 = context.mock(Task.class, "task2");
        DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact(getTestName(), getTestExt(), getTestType(),
                getTestClassifier(), getDate(), getTestFile(), task1, task2);
        assertThat((Set<Task>) publishArtifact.getBuildDependencies().getDependencies(null), equalTo(toSet(task1, task2)));
        assertCommonPropertiesAreSet(publishArtifact, true);
    }

    @Test
    public void canSpecifyTheBuilderTasksOnConstruction() {
        Task task = context.mock(Task.class);
        DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact("name", "extension", "type", null, null, null, task);

        assertThat((Set<Task>)publishArtifact.getBuildDependencies().getDependencies(null), equalTo(toSet(task)));
    }
    
    @Test
    public void canSpecifyTheBuilderTasks() {
        Task task = context.mock(Task.class);
        DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact("name", "extension", "type", null, null, null);
        publishArtifact.builtBy(task);

        assertThat((Set<Task>) publishArtifact.getBuildDependencies().getDependencies(null), equalTo(toSet(task)));
    }
}
