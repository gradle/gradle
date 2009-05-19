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
package org.gradle.api.internal.artifacts.dependencies;

import groovy.lang.Closure;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
abstract public class AbstractDependencyTest {
    
    protected abstract AbstractDependency getDependency();

    protected abstract AbstractDependency createDependency(String group, String name, String version);

    protected abstract AbstractDependency createDependency(String group, String name, String version, String configuration);

    protected JUnit4Mockery context = new JUnit4GroovyMockery();

    @Test
    public void testGenericInit() {
        assertTrue(getDependency().getArtifacts().isEmpty());
        assertTrue(getDependency().getExcludeRules().isEmpty());
        assertThat(getDependency().getConfiguration(), equalTo(Dependency.DEFAULT_CONFIGURATION));
//        assertThat(getDependency().getState(), equalTo(Dependency.State.UNRESOLVED));
    }
    
    @Test
    public void exclude() {
        Map<String,String> excludeArgs1 = WrapUtil.toMap("key", "value");
        Map<String,String> excludeArgs2 = WrapUtil.toMap("key2", "value2");

        getDependency().exclude(excludeArgs1);
        getDependency().exclude(excludeArgs2);

        assertThat(getDependency().getExcludeRules().size(), equalTo(2));
        assertThat(getDependency().getExcludeRules(), Matchers.<ExcludeRule>hasItem(new DefaultExcludeRule(excludeArgs1)));
        assertThat(getDependency().getExcludeRules(), Matchers.<ExcludeRule>hasItem(new DefaultExcludeRule(excludeArgs2)));
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

    private Closure createClosureThatSetsArtifactName(String artifactName) {
        return HelperUtil.toClosure(String.format("{ name='%s' }", artifactName));
    }

    private void assertDependencyHasNamedArtifacts(String... names) {
        assertThat(getDependency().getArtifacts().size(), equalTo(names.length));
        Set<String> foundNames = new HashSet<String>();
        for (DependencyArtifact artifact : getDependency().getArtifacts()) {
            for (String name : names) {
                if (artifact.getName().equals(name)) {
                    foundNames.add(name);
                }
                continue;
            }
        }
        assertThat(foundNames.size(), equalTo(names.length));
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

    protected void assertDeepCopy(Dependency dependency, Dependency copiedDependency) {
        assertThat(dependency.contentEquals(copiedDependency), equalTo(true));
        assertThat(copiedDependency, not(sameInstance(dependency)));
        assertThat(copiedDependency.getArtifacts(), not(sameInstance(dependency.getArtifacts())));
        assertThat(copiedDependency.getExcludeRules(), not(sameInstance(dependency.getExcludeRules())));
        assertThat(copiedDependency.getConfiguration(), equalTo(dependency.getConfiguration()));
    }

//    @Test(expected = InvalidUserDataException.class)
//    public void getFilesWithStateUnresolved() {
//        getDependency().getFiles();
//    }
//
//    @Test(expected = InvalidUserDataException.class)
//    public void getFilesWithStateUnresolvable() {
//        getDependency().setResolveData(null);
//        getDependency().getFiles();
//    }
//
//    @Test
//    public void setResolveDataWithFiles() {
//        List<File> resolvedFiles = WrapUtil.toList(new File("somePath"));
//        getDependency().setResolveData(resolvedFiles);
//
//        assertThat(getDependency().getState(), equalTo(Dependency.State.RESOLVED));
//        assertThat(getDependency().getFiles(), equalTo(resolvedFiles));
//        assertThatStateCantBeChangedAnymore();
//    }
//
//    @Test
//    public void setResolveDataWithNull() {
//        getDependency().setResolveData(null);
//
//        assertThat(getDependency().getState(), equalTo(Dependency.State.UNRESOLVABLE));
//        assertThat(getDependency().getFiles().size(), equalTo(0));
//        assertThatStateCantBeChangedAnymore();
//    }
//
//    private void assertThatStateCantBeChangedAnymore() {
//        try {
//            getDependency().setTransitive(true);
//            fail();
//        } catch (InvalidUserDataException e) {
//            // ignore
//        }
//    }
}
