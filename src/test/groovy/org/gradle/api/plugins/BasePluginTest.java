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
package org.gradle.api.plugins;

import org.gradle.api.Project;
import org.gradle.api.internal.DefaultTask;
import org.gradle.api.tasks.Clean;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class BasePluginTest {
    @Test
    public void addsTasksAndConventionToProject() {
        Project project = HelperUtil.createRootProject();
        new BasePlugin().apply(project, null, null);

        assertThat(project.task("clean"), instanceOf(Clean.class));
        assertThat(project.task("init"), instanceOf(DefaultTask.class));
        assertThat(project.getConvention().getPlugins().get("base"), instanceOf(BasePluginConvention.class));
    }
}
