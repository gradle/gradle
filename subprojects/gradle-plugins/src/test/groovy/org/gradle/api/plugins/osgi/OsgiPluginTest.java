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
package org.gradle.api.plugins.osgi;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.util.HelperUtil;

// todo Make test stronger
public class OsgiPluginTest {
    private final Project project = HelperUtil.createRootProject();
    private final OsgiPlugin osgiPlugin = new OsgiPlugin();
    
    @Test
    public void appliesTheJavaPlugin() {
        osgiPlugin.apply(project);

        assertTrue(project.getPlugins().hasPlugin(JavaPlugin.class));
    }

    @Test
    public void addsAnOsgiManifestToEachJar() {
        osgiPlugin.apply(project);

        Task task = project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
        assertThat(task.property("osgi"), is(OsgiManifest.class));

        task = project.getTasks().add("otherJar", Jar.class);
        assertThat(task.property("osgi"), is(OsgiManifest.class));
    }
}
