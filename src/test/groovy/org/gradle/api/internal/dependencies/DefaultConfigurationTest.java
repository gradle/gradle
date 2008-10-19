/*
 * Copyright 2007, 2008 the original author or authors.
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
package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.descriptor.Configuration;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

@RunWith (JMock.class)
public class DefaultConfigurationTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final DependencyManagerInternal dependencyManager = context.mock(DependencyManagerInternal.class);
    private final DefaultConfiguration configuration = new DefaultConfiguration("name", dependencyManager, null);

    @Test
    public void defaultValues() {
        assertThat(configuration.getIvyConfiguration().getName(), equalTo("name"));
        assertThat(configuration.getIvyConfiguration().getVisibility(), equalTo(Configuration.Visibility.PUBLIC));
        assertThat(configuration.getIvyConfiguration().getExtends().length, equalTo(0));
        assertThat(configuration.getIvyConfiguration().isTransitive(), equalTo(true));
        assertThat(configuration.getIvyConfiguration().getDescription(), nullValue());
        assertThat(configuration.getIvyConfiguration().getDeprecated(), nullValue());
    }

    @Test
    public void withPrivateVisibility() {
        configuration.setPrivate(true);
        assertTrue(configuration.isPrivate());
        assertThat(configuration.getIvyConfiguration().getVisibility(), equalTo(Configuration.Visibility.PRIVATE));
    }

    @Test
    public void withIntransitive() {
        configuration.setTransitive(false);
        assertFalse(configuration.isTransitive());
        assertThat(configuration.getIvyConfiguration().isTransitive(), equalTo(false));
    }

    @Test
    public void extendsOtherConfigurations() {
        configuration.extendsConfiguration(toArray("a"));

        assertThat(configuration.getExtendsConfiguration(), equalTo(toSet("a")));
        assertThat(configuration.getIvyConfiguration().getExtends(), equalTo(toArray("a")));
    }

    @Test
    public void usesProvidedIvyConfigurationAsATemplate() {
        Configuration ivyConfiguration = new Configuration("name", Configuration.Visibility.PRIVATE, "description",
                toArray("a"), false, "dep");
        DefaultConfiguration configuration = new DefaultConfiguration("name", dependencyManager, ivyConfiguration);

        assertThat(configuration.getIvyConfiguration().getName(), equalTo("name"));
        assertThat(configuration.getIvyConfiguration().getVisibility(), equalTo(Configuration.Visibility.PRIVATE));
        assertThat(configuration.getIvyConfiguration().getExtends(), equalTo(toArray("a")));
        assertThat(configuration.getIvyConfiguration().isTransitive(), equalTo(false));
        assertThat(configuration.getIvyConfiguration().getDescription(), equalTo("description"));
        assertThat(configuration.getIvyConfiguration().getDeprecated(), equalTo("dep"));

        configuration.extendsConfiguration(toArray("b"));

        assertThat(configuration.getIvyConfiguration().getExtends(), equalTo(toArray("a", "b")));
    }

    @Test
    public void usesDependencyManagerToResolveConfiguration() {
        final File file = new File("lib.jar");
        context.checking(new Expectations() {{
            one(dependencyManager).resolve("name");
            will(returnValue(toList(file)));
        }});

        assertThat(configuration.resolve(), equalTo(toSet(file)));
    }

    @Test
    public void usesDependencyManagerToResolveAsPath() {
        context.checking(new Expectations() {{
            one(dependencyManager).antpath("name");
            will(returnValue("the path"));
        }});

        assertThat(configuration.asPath(), equalTo("the path"));
    }

}
