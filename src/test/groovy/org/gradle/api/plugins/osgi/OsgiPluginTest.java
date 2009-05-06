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

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;

import java.util.HashMap;

// todo Make test stronger
public class OsgiPluginTest {
    private final Project project = HelperUtil.createRootProject();
    private final OsgiPlugin osgiPlugin = new OsgiPlugin();
    
    @Test
    public void appliesTheJavaPlugin() {
        osgiPlugin.apply(project, new PluginRegistry(), null);

        assertTrue(project.getAppliedPlugins().contains(JavaPlugin.class));
    }

    @Test
    public void addsAnOsgiManifestToEachJar() {
        osgiPlugin.apply(project, new PluginRegistry(), null);

        Task task = project.task(JavaPlugin.JAR_TASK_NAME);
        assertThat(task.property("osgi"), is(OsgiManifest.class));

        task = project.getTasks().add("otherJar", Jar.class);
        assertThat(task.property("osgi"), is(OsgiManifest.class));
    }
}
