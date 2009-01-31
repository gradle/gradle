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
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

public class DefaultConfigurationTest {
    private final ConfigurationContainer configurationContainer = new DefaultConfigurationContainer();
    private final DefaultConfiguration configuration = new DefaultConfiguration("name", configurationContainer);

    @Test
    public void defaultValues() {
        assertThat(configuration.getResolveInstruction(), not(equalTo(null)));
        assertThat(getIvyConfiguration().getName(), equalTo("name"));
        assertThat(getIvyConfiguration().getVisibility(), equalTo(Configuration.Visibility.PUBLIC));
        assertThat(getIvyConfiguration().getExtends().length, equalTo(0));
        assertThat(getIvyConfiguration().isTransitive(), equalTo(true));
        assertThat(getIvyConfiguration().getDescription(), nullValue());
        assertThat(getIvyConfiguration().getDeprecated(), nullValue());
    }

    private Configuration getIvyConfiguration() {
        return configuration.getIvyConfiguration(true);
    }

    @Test
    public void withPrivateVisibility() {
        configuration.setVisible(false);                                                                          
        assertFalse(configuration.isVisible());
        assertThat(getIvyConfiguration().getVisibility(), equalTo(Configuration.Visibility.PRIVATE));
    }

    @Test
    public void withIntransitive() {
        configuration.setTransitive(false);
        assertFalse(configuration.isTransitive());
        assertThat(configuration.getIvyConfiguration(true).isTransitive(), equalTo(true));
        assertThat(configuration.getIvyConfiguration(false).isTransitive(), equalTo(false));
    }

    @Test
    public void withDescription() {
        configuration.setDescription("description");
        assertThat(configuration.getDescription(), equalTo("description"));
        assertThat(getIvyConfiguration().getDescription(), equalTo("description"));
    }

    @Test
    public void extendsOtherConfigurations() {
        String testConf1 = "testConf1";
        String testConf2 = "testConf2";

        configurationContainer.add(testConf1);
        configuration.extendsFrom(testConf1);
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configurationContainer.get(testConf1))));
        assertThat(getIvyConfiguration().getExtends(), equalTo(toArray(testConf1)));

        configurationContainer.add(testConf2);
        configuration.extendsFrom(testConf2);
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configurationContainer.get(testConf1), configurationContainer.get(testConf2))));
        assertThat(getIvyConfiguration().getExtends(), equalTo(toArray(testConf1, testConf2)));
    }

    @Test
    public void setExtendsFrom() {
        String testConf1 = "testConf1";
        String testConf2 = "testConf2";

        configurationContainer.add(testConf1);
        configuration.setExtendsFrom(WrapUtil.toSet(testConf1));
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configurationContainer.get(testConf1))));

        configurationContainer.add(testConf2);
        configuration.setExtendsFrom(WrapUtil.toSet(testConf2));
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configurationContainer.get(testConf1), configurationContainer.get(testConf2))));
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
        assertThat(getIvyConfiguration(), sameInstance(transformed));
    }
    
    @Test
    public void transformsIvyConfigurationObjectUsingClosure() {
        final Configuration transformed = new Configuration("other");
        Closure closure = HelperUtil.returns(transformed);

        configuration.addIvyTransformer(closure);
        assertThat(getIvyConfiguration(), sameInstance(transformed));
    }

    @Test
    public void getChain() {
        Set<org.gradle.api.dependencies.Configuration> expectedConfigurations = new HashSet<org.gradle.api.dependencies.Configuration>();
        expectedConfigurations.add(configurationContainer.add("root1"));
        expectedConfigurations.add(configurationContainer.add("root2"));
        configurationContainer.add("root3");
        expectedConfigurations.add(configurationContainer.add("middle1").extendsFrom("root1"));
        expectedConfigurations.add(configurationContainer.add("middle2").extendsFrom("root1", "root2"));
        org.gradle.api.dependencies.Configuration leaf = configurationContainer.add("leaf1").extendsFrom("middle1", "middle2");
        expectedConfigurations.add(leaf);
        assertThat((Set<org.gradle.api.dependencies.Configuration>) leaf.getChain(), equalTo(expectedConfigurations));
    }
}
