/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.dependencies.*;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;import static org.junit.Assert.assertThat;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.hamcrest.Matchers;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
@RunWith(Parameterized.class)
public class GenericDependencyTest {
    protected Dependency dependency;
    protected ExcludeRuleContainer excludeRuleContainerMock;
    protected DependencyConfigurationMappingContainer dependencyConfigurationMappingContainerMock;

    protected JUnit4Mockery context = new JUnit4Mockery();

    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][]{
                {new DefaultModuleDependency(AbstractDependencyTest.TEST_CONF_MAPPING, "org:name:1.0")},
                {new DefaultProjectDependency(AbstractDependencyTest.TEST_CONF_MAPPING,
                        HelperUtil.createRootProject(new File("a")),
                        HelperUtil.createRootProject(new File("a")))},
                {new ClientModule(AbstractDependencyTest.TEST_CONF_MAPPING, "org:name:1.0", null)}
        });
    }

    public GenericDependencyTest(Dependency dependency) {
        this.dependency = dependency;
    }


    @Before
    public void setUp() {
        excludeRuleContainerMock = context.mock(ExcludeRuleContainer.class);
        dependencyConfigurationMappingContainerMock = context.mock(DependencyConfigurationMappingContainer.class);
        dependency.setExcludeRules(excludeRuleContainerMock);
    }

    @Test
    public void exclude() {
        final Map<String,String> excludeRule1 = WrapUtil.toMap("org1", "somevalue");
        final Map<String, String> excludeRule2 = WrapUtil.toMap("org2", "somevalue2");
        context.checking(new Expectations() {{
            one(excludeRuleContainerMock).add(excludeRule1);
            one(excludeRuleContainerMock).add(excludeRule2);
        }});
        dependency.exclude(excludeRule1);
        dependency.exclude(excludeRule2);
        context.assertIsSatisfied();
    }

    @Test
    public void excludeWithConfs() {
        final List<String> confs1 = WrapUtil.toList("conf11", "conf12");
        final List<String> confs2 = WrapUtil.toList("conf21", "conf22");
        final Map<String,String> excludeRule1 = WrapUtil.toMap("org1", "somevalue");
        final Map<String, String> excludeRule2 = WrapUtil.toMap("org2", "somevalue2");
        context.checking(new Expectations() {{
            one(excludeRuleContainerMock).add(excludeRule1, confs1);
            one(excludeRuleContainerMock).add(excludeRule2, confs2);
        }});
        dependency.exclude(excludeRule1, confs1);
        dependency.exclude(excludeRule2, confs2);
        context.assertIsSatisfied();
    }

    @Test
    public void dependencyConfiguration() {
        dependency.setDependencyConfigurationMappings(dependencyConfigurationMappingContainerMock);
        final String[] testDependencyConfigurations = WrapUtil.toArray("conf2", "conf2");
        context.checking(new Expectations() {{
            one(dependencyConfigurationMappingContainerMock).add(testDependencyConfigurations);
        }});
        dependency.addDependencyConfiguration(testDependencyConfigurations);
        context.assertIsSatisfied();
    }

    @Test
    public void dependencyConfigurationWithConstraints() {
        dependency.setDependencyConfigurationMappings(dependencyConfigurationMappingContainerMock);
        final Map<Configuration, List<String>> testDependencyConfigurations = WrapUtil.toMap(context.mock(Configuration.class, "master"), WrapUtil.toList("depConf"));
        context.checking(new Expectations() {{
            one(dependencyConfigurationMappingContainerMock).add(testDependencyConfigurations);
        }});
        dependency.addConfigurationMapping(testDependencyConfigurations);
        context.assertIsSatisfied();
    }

    @Test
    public void artifacts() {
        assertEquals(0, dependency.getArtifacts().size());
        Artifact artifact = new Artifact("name1", "type1", "ext1", "classifier1", "url");
        dependency.addArtifact(artifact);
        assertEquals(1, dependency.getArtifacts().size());
        assertSame(artifact, dependency.getArtifacts().get(0));
        Artifact artifact2 = dependency.artifact(HelperUtil.createSetterClosure("Name", "testname"));
        assertEquals("testname", artifact2.getName());
        assertEquals(2, dependency.getArtifacts().size());
        assertThat(dependency.getArtifacts(), Matchers.hasItems(artifact, artifact2));
        context.assertIsSatisfied();
    }
}
