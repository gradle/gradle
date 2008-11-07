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

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.gradle.api.Transformer;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith (JMock.class)
public class DefaultConfigurationTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final DependencyManagerInternal dependencyManager = context.mock(DependencyManagerInternal.class);
    private final DefaultConfiguration configuration = new DefaultConfiguration("name", dependencyManager);

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
        configuration.setVisible(false);
        assertFalse(configuration.isVisible());
        assertThat(configuration.getIvyConfiguration().getVisibility(), equalTo(Configuration.Visibility.PRIVATE));
    }

    @Test
    public void withIntransitive() {
        configuration.setTransitive(false);
        assertFalse(configuration.isTransitive());
        assertThat(configuration.getIvyConfiguration().isTransitive(), equalTo(false));
    }

    @Test
    public void withDescription() {
        configuration.setDescription("description");
        assertThat(configuration.getDescription(), equalTo("description"));
        assertThat(configuration.getIvyConfiguration().getDescription(), equalTo("description"));
    }

    @Test
    public void extendsOtherConfigurations() {
        configuration.extendsFrom("a");

        assertThat(configuration.getExtendsFrom(), equalTo(toSet("a")));
        assertThat(configuration.getIvyConfiguration().getExtends(), equalTo(toArray("a")));
    }

    @Test
    public void transformsIvyConfigurationObject() {
        final Configuration transformed = new Configuration("other");
        Transformer<Configuration> transformer = new Transformer<Configuration>() {
            public Configuration transform(Configuration original) {
                assertThat(original, notNullValue());
                return transformed;
            }
        };

        configuration.addIvyTransformer(transformer);
        assertThat(configuration.getIvyConfiguration(), sameInstance(transformed));
    }
    
    @Test
    public void transformsIvyConfigurationObjectUsingClosure() {
        final Configuration transformed = new Configuration("other");
        Closure closure = DefaultConfigurationTestHelper.transformer(transformed);

        configuration.addIvyTransformer(closure);
        assertThat(configuration.getIvyConfiguration(), sameInstance(transformed));
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
    public void usesDependencyManagerToGetFiles() {
        final File file = new File("lib.jar");
        context.checking(new Expectations() {{
            one(dependencyManager).resolve("name");
            will(returnValue(toList(file)));
        }});

        assertThat(configuration.getFiles(), equalTo(toSet(file)));
    }

    @Test
    public void usesDependencyManagerToGetFilesForIterator() {
        final File expected = new File("lib.jar");
        context.checking(new Expectations() {{
            one(dependencyManager).resolve("name");
            will(returnValue(toList(expected)));
        }});

        assertThat(toList(configuration), equalTo(toList(expected)));
    }

    @Test
    public void usesDependencyManagerToGetSingleFile() {
        final File file = new File("lib.jar");
        context.checking(new Expectations() {{
            one(dependencyManager).resolve("name");
            will(returnValue(toList(file)));
        }});

        assertThat(configuration.getSingleFile(), equalTo(file));
    }

    @Test
    public void getSingleFileFailsWhenThereIsNotExactlyOneFile() {
        context.checking(new Expectations() {{
            one(dependencyManager).resolve("name");
            will(returnValue(toList(new File("a.jar"), new File("b.jar"))));
        }});

        try {
            configuration.getSingleFile();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Configuration 'name' does not resolve to a single file."));
        }
    }

    @Test
    public void usesDependencyManagerToResolveAsPath() {
        context.checking(new Expectations() {{
            one(dependencyManager).antpath("name");
            will(returnValue("the path"));
        }});

        assertThat(configuration.getAsPath(), equalTo("the path"));
    }

}
