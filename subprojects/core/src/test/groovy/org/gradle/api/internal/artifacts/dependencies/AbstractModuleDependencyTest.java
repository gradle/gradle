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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static org.gradle.util.Matchers.isEmpty;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(JMock.class)
abstract public class AbstractModuleDependencyTest {
    //TODO SF rework the remaining coverage of this hierarchy in the spirit of AbstractModuleDependencySpec and DefaultProjectDependencyTest

    protected abstract AbstractModuleDependency getDependency();

    protected abstract AbstractModuleDependency createDependency(String group, String name, String version);

    protected abstract AbstractModuleDependency createDependency(String group, String name, String version, String configuration);

    protected JUnit4Mockery context = new JUnit4GroovyMockery();

    @Test
    public void defaultValues() {
        assertTrue(getDependency().isTransitive());
        assertThat(getDependency().getArtifacts(), isEmpty());
        assertThat(getDependency().getExcludeRules(), isEmpty());
        assertThat(getDependency().getConfiguration(), equalTo(Dependency.DEFAULT_CONFIGURATION));
    }

    @Test
    public void exclude() {
        Map<String, String> excludeArgs1 = WrapUtil.toMap("group", "aGroup");
        Map<String, String> excludeArgs2 = WrapUtil.toMap("module", "aModule");

        getDependency().exclude(excludeArgs1);
        getDependency().exclude(excludeArgs2);

        assertThat(getDependency().getExcludeRules().size(), equalTo(2));
        assertThat(getDependency().getExcludeRules(), hasItem((ExcludeRule) new DefaultExcludeRule("aGroup", null)));
        assertThat(getDependency().getExcludeRules(), hasItem((ExcludeRule) new DefaultExcludeRule(null, "aModule")));
    }

    @Test
    public void addArtifact() {
        DependencyArtifact artifact1 = createAnonymousArtifact();
        DependencyArtifact artifact2 = createAnonymousArtifact();

        getDependency().addArtifact(artifact1);
        getDependency().addArtifact(artifact2);

        assertThat(getDependency().getArtifacts().size(), equalTo(2));
        assertThat(getDependency().getArtifacts(), hasItem(artifact1));
        assertThat(getDependency().getArtifacts(), hasItem(artifact2));
    }

    private DependencyArtifact createAnonymousArtifact() {
        return new DefaultDependencyArtifact(HelperUtil.createUniqueId(), "type", "org", "classifier", "url");
    }

    @Test
    public void equality() {
        assertThat(createDependency("group1", "name1", "version1"), equalTo(createDependency("group1", "name1", "version1")));
        assertThat(createDependency("group1", "name1", "version1").hashCode(), equalTo(createDependency("group1", "name1", "version1").hashCode()));
        assertThat(createDependency("group1", "name1", "version1"), not(equalTo(createDependency("group1", "name1", "version2"))));
        assertThat(createDependency("group1", "name1", "version1"), not(equalTo(createDependency("group1", "name2", "version1"))));
        assertThat(createDependency("group1", "name1", "version1"), not(equalTo(createDependency("group2", "name1", "version1"))));
        assertThat(createDependency("group1", "name1", "version1"), not(equalTo(createDependency("group2", "name1", "version1"))));
        assertThat(createDependency("group1", "name1", "version1", "depConf1"), not(equalTo(createDependency("group1", "name1", "version1", "depConf2"))));
    }

    protected void assertDeepCopy(ModuleDependency dependency, ModuleDependency copiedDependency) {
        assertThat(copiedDependency.getGroup(), equalTo(dependency.getGroup()));
        assertThat(copiedDependency.getName(), equalTo(dependency.getName()));
        assertThat(copiedDependency.getVersion(), equalTo(dependency.getVersion()));
        assertThat(copiedDependency.getConfiguration(), equalTo(dependency.getConfiguration()));
        assertThat(copiedDependency.isTransitive(), equalTo(dependency.isTransitive()));
        assertThat(copiedDependency.getArtifacts(), equalTo(dependency.getArtifacts()));
        assertThat(copiedDependency.getArtifacts(), not(sameInstance(dependency.getArtifacts())));
        assertThat(copiedDependency.getExcludeRules(), equalTo(dependency.getExcludeRules()));
        assertThat(copiedDependency.getExcludeRules(), not(sameInstance(dependency.getExcludeRules())));
    }
}