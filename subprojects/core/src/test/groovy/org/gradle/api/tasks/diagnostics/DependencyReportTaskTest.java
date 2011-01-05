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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.diagnostics.internal.AsciiReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.util.WrapUtil;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(JMock.class)
public class DependencyReportTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private Project project;
    private DependencyReportTask task;

    @Before
    public void setup() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        project = context.mock(ProjectInternal.class);

        context.checking(new Expectations() {{
            allowing(project).absoluteProjectPath("list");
            will(returnValue(":path"));
            allowing(project).getConvention();
            will(returnValue(null));
        }});

        task = HelperUtil.createTask(DependencyReportTask.class);
    }

    @Test
    public void init() {
        assertThat(task.getRenderer(), instanceOf(AsciiReportRenderer.class));
        assertThat(task.getConfigurations(), nullValue());
    }

    @Test
    public void passesEachProjectConfigurationToRenderer() throws IOException {
        final ConfigurationContainer configurationContainer = context.mock(ConfigurationContainer.class);
        final Configuration configuration1 = context.mock(Configuration.class, "Configuration1");
        final Configuration configuration2 = context.mock(Configuration.class, "Configuration2");
        context.checking(new Expectations() {{
            allowing(project).getConfigurations();
            will(returnValue(configurationContainer));

            allowing(configurationContainer).getAll();
            will(returnValue(WrapUtil.toSet(configuration1, configuration2)));
        }});
        assertConfigurationsIsPassedToRenderer(configuration1, configuration2);
    }

    @Test
    public void passesSpecifiedConfigurationToRenderer() throws IOException {
        final Configuration configuration1 = context.mock(Configuration.class, "Configuration1");
        final Configuration configuration2 = context.mock(Configuration.class, "Configuration2");
        task.setConfigurations(WrapUtil.toSet(configuration1, configuration2));
        assertConfigurationsIsPassedToRenderer(configuration1, configuration2);
    }

    private void assertConfigurationsIsPassedToRenderer(final Configuration configuration1, final Configuration configuration2) throws IOException {
        final DependencyReportRenderer renderer = context.mock(DependencyReportRenderer.class);
        final ResolvedConfiguration resolvedConfiguration1 = context.mock(ResolvedConfiguration.class, "ResolvedConf1");
        final ResolvedConfiguration resolvedConfiguration2 = context.mock(ResolvedConfiguration.class, "ResolvedConf2");

        task.setRenderer(renderer);
        task.setConfigurations(WrapUtil.toSet(configuration1, configuration2));

        context.checking(new Expectations() {{
            allowing(configuration1).getName();
            will(returnValue("config1"));

            allowing(configuration2).getName();
            will(returnValue("config2"));

            Sequence resolve = context.sequence("resolve");
            Sequence render = context.sequence("render");

            one(configuration1).getResolvedConfiguration();
            inSequence(resolve);
            will(returnValue(resolvedConfiguration1));

            one(renderer).startConfiguration(configuration1);
            inSequence(render);

            one(renderer).render(resolvedConfiguration1);
            inSequence(render);

            one(renderer).completeConfiguration(configuration1);
            inSequence(render);

            one(configuration2).getResolvedConfiguration();
            inSequence(resolve);
            will(returnValue(resolvedConfiguration2));

            one(renderer).startConfiguration(configuration2);
            inSequence(render);

            one(renderer).render(resolvedConfiguration2);
            inSequence(render);

            one(renderer).completeConfiguration(configuration2);
            inSequence(render);
        }});
        task.generate(project);
    }


}
