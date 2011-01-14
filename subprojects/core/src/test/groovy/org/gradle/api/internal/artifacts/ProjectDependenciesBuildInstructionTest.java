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
package org.gradle.api.internal.artifacts;

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
public class ProjectDependenciesBuildInstructionTest {
    @Test
    public void initWithNull() {
        ProjectDependenciesBuildInstruction buildInstruction = new ProjectDependenciesBuildInstruction(false);
        assertThat(buildInstruction.isRebuild(), equalTo(false));
    }

    @Test
    public void initWithEmptyList() {
        ProjectDependenciesBuildInstruction buildInstruction = new ProjectDependenciesBuildInstruction(true);
        assertThat(buildInstruction.isRebuild(), equalTo(true));
    }

    @Test
    public void equality() {
        assertThat(new ProjectDependenciesBuildInstruction(false),
                equalTo(new ProjectDependenciesBuildInstruction(false)));
        assertThat(new ProjectDependenciesBuildInstruction(true),
                equalTo(new ProjectDependenciesBuildInstruction(true)));

        assertThat(new ProjectDependenciesBuildInstruction(false),
                not(equalTo(new ProjectDependenciesBuildInstruction(true))));
    }

    @Test
    public void hashCodeEquality() {
        assertThat(new ProjectDependenciesBuildInstruction(false).hashCode(),
                equalTo(new ProjectDependenciesBuildInstruction(false).hashCode()));
        assertThat(new ProjectDependenciesBuildInstruction(true).hashCode(),
                equalTo(new ProjectDependenciesBuildInstruction(true).hashCode()));
    }
}
