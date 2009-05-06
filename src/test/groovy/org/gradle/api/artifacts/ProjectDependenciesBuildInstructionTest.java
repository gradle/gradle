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
package org.gradle.api.artifacts;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.*;
import org.gradle.util.WrapUtil;

import java.util.Collections;

/**
 * @author Hans Dockter
 */
public class ProjectDependenciesBuildInstructionTest {
    @Test
    public void initWithNull() {
        ProjectDependenciesBuildInstruction buildInstruction = new ProjectDependenciesBuildInstruction(null);
        assertThat(buildInstruction.isRebuild(), equalTo(false));
        assertThat(buildInstruction.getTaskNames(), equalTo(Collections.<String>emptyList()));
    }

    @Test
    public void initWithEmptyList() {
        ProjectDependenciesBuildInstruction buildInstruction = new ProjectDependenciesBuildInstruction(Collections.<String>emptyList());
        assertThat(buildInstruction.isRebuild(), equalTo(true));
        assertThat(buildInstruction.getTaskNames(), equalTo(Collections.<String>emptyList()));
    }

    @Test
    public void initWithNonEmptyList() {
        String taskName = "someTaskName";
        ProjectDependenciesBuildInstruction buildInstruction = new ProjectDependenciesBuildInstruction(
                WrapUtil.toList(taskName));
        assertThat(buildInstruction.isRebuild(), equalTo(true));
        assertThat(buildInstruction.getTaskNames(), equalTo(WrapUtil.toList(taskName)));
    }

    @Test
    public void equality() {
        String taskName = "someTaskName";
        assertThat(new ProjectDependenciesBuildInstruction(null),
                equalTo(new ProjectDependenciesBuildInstruction(null)));
        assertThat(new ProjectDependenciesBuildInstruction(Collections.<String>emptyList()),
                equalTo(new ProjectDependenciesBuildInstruction(Collections.<String>emptyList())));
        assertThat(new ProjectDependenciesBuildInstruction(WrapUtil.toList(taskName)),
                equalTo(new ProjectDependenciesBuildInstruction(WrapUtil.toList(taskName))));

        assertThat(new ProjectDependenciesBuildInstruction(null),
                not(equalTo(new ProjectDependenciesBuildInstruction(Collections.<String>emptyList()))));
        assertThat(new ProjectDependenciesBuildInstruction(WrapUtil.toList(taskName)),
                not(equalTo(new ProjectDependenciesBuildInstruction(WrapUtil.toList(taskName + 'x')))));
    }

    @Test
    public void hashCodeEquality() {
        String taskName = "someTaskName";
        assertThat(new ProjectDependenciesBuildInstruction(null).hashCode(),
                equalTo(new ProjectDependenciesBuildInstruction(null).hashCode()));
        assertThat(new ProjectDependenciesBuildInstruction(Collections.<String>emptyList()).hashCode(),
                equalTo(new ProjectDependenciesBuildInstruction(Collections.<String>emptyList()).hashCode()));
        assertThat(new ProjectDependenciesBuildInstruction(WrapUtil.toList(taskName)).hashCode(),
                equalTo(new ProjectDependenciesBuildInstruction(WrapUtil.toList(taskName)).hashCode()));
    }
}
