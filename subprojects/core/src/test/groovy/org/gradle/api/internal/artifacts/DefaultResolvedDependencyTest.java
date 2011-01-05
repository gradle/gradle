/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedDependencyTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    @Test
    public void init() {
        String someGroup = "someGroup";
        String someName = "someName";
        String someVersion = "someVersion";
        String someConfiguration = "someConfiguration";
        Set<ResolvedArtifact> someArtifacts = WrapUtil.toSet(createArtifact("someName"));
        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency(someGroup, someName, someVersion, someConfiguration,
                someArtifacts);
        assertThat(resolvedDependency.getName(), equalTo(someGroup + ":" + someName + ":" + someVersion));
        assertThat(resolvedDependency.getModuleGroup(), equalTo(someGroup));
        assertThat(resolvedDependency.getModuleName(), equalTo(someName));
        assertThat(resolvedDependency.getModuleVersion(), equalTo(someVersion));
        assertThat(resolvedDependency.getConfiguration(), equalTo(someConfiguration));
        assertThat(resolvedDependency.getModuleArtifacts(), equalTo(someArtifacts));
        assertThat(resolvedDependency.getChildren(), equalTo(Collections.<ResolvedDependency>emptySet()));
        assertThat(resolvedDependency.getParents(), equalTo(Collections.<ResolvedDependency>emptySet()));
    }

    @Test
    public void getAllModuleArtifacts() {
        Set<ResolvedArtifact> someModuleArtifacts = WrapUtil.<ResolvedArtifact>toSet(createArtifact("moduleArtifact"));
        Set<ResolvedArtifact> someChildModuleArtifacts = WrapUtil.<ResolvedArtifact>toSet(createArtifact("childModuleArtifact"));
        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someGroup", "someName", "someVersion", "someConfiguration",
                someModuleArtifacts);
        resolvedDependency.getChildren().add(new DefaultResolvedDependency("someGroup", "someChild", "someVersion", "someChildConfiguration",
                someChildModuleArtifacts));
        assertThat(resolvedDependency.getAllModuleArtifacts(), equalTo(GUtil.addSets(someChildModuleArtifacts, someModuleArtifacts)));
    }

    @Test
    public void getParentArtifacts() {
        Set<ResolvedArtifact> someModuleArtifacts = WrapUtil.toSet(createArtifact("someResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(someModuleArtifacts);

        Set<ResolvedArtifact> parent1SpecificArtifacts = WrapUtil.toSet(createArtifact("parent1Specific"));
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts);

        Set<ResolvedArtifact> parent2SpecificArtifacts = WrapUtil.toSet(createArtifact("parent2Specific"));
        DefaultResolvedDependency parentResolvedDependency2 = createAndAddParent("parent2", resolvedDependency, parent2SpecificArtifacts);

        assertThat(resolvedDependency.getParentArtifacts(parentResolvedDependency1), equalTo(parent1SpecificArtifacts));
        assertThat(resolvedDependency.getParentArtifacts(parentResolvedDependency2), equalTo(parent2SpecificArtifacts));
    }

    private ResolvedArtifact createArtifact(String name) {
        return DefaultResolvedArtifactTest.createResolvedArtifact(context, name, "someType", "someExt", new File("pathTo" + name));
    }

    private DefaultResolvedDependency createResolvedDependency(Set<ResolvedArtifact> moduleArtifacts) {
        return new DefaultResolvedDependency("someGroup", "someName", "someVersion", "someConfiguration",
                moduleArtifacts);
    }

    @Test
    public void getArtifacts() {
        Set<ResolvedArtifact> someModuleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(someModuleArtifacts);

        Set<ResolvedArtifact> parent1SpecificArtifacts = WrapUtil.toSet(createArtifact("parent1Specific"));
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts);

        assertThat(resolvedDependency.getArtifacts(parentResolvedDependency1), equalTo(GUtil.addSets(someModuleArtifacts, parent1SpecificArtifacts)));
    }

    @Test
    public void getArtifactsWithParentWithoutParentArtifacts() {
        Set<ResolvedArtifact> moduleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(moduleArtifacts);

        DefaultResolvedDependency parent = new DefaultResolvedDependency("someGroup", "parent", "someVersion", "someConfiguration",
                Collections.<ResolvedArtifact>emptySet());
        resolvedDependency.getParents().add(parent);
        assertThat(resolvedDependency.getArtifacts(parent), equalTo(moduleArtifacts));
    }

    @Test
    public void getParentArtifactsWithParentWithoutParentArtifacts() {
        Set<ResolvedArtifact> moduleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(moduleArtifacts);

        DefaultResolvedDependency parent = new DefaultResolvedDependency("someGroup", "parent", "someVersion", "someConfiguration",
                Collections.<ResolvedArtifact>emptySet());
        resolvedDependency.getParents().add(parent);
        assertThat(resolvedDependency.getParentArtifacts(parent), equalTo(Collections.<ResolvedArtifact>emptySet()));
    }

    @Test(expected = InvalidUserDataException.class)
    public void getParentArtifactsWithUnknownParent() {
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(Collections.<ResolvedArtifact>emptySet());
        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency("someGroup", "parent2", "someVersion", "someConfiguration",
                Collections.<ResolvedArtifact>emptySet());
        assertThat(resolvedDependency.getParentArtifacts(unknownParent),
                equalTo(Collections.<ResolvedArtifact>emptySet()));
    }

    @Test(expected = InvalidUserDataException.class)
    public void getArtifactsWithUnknownParent() {
        Set<ResolvedArtifact> someModuleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(Collections.<ResolvedArtifact>emptySet());

        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency("someGroup", "parent2", "someVersion", "someConfiguration",
                Collections.<ResolvedArtifact>emptySet());
        assertThat(resolvedDependency.getParentArtifacts(unknownParent),
                equalTo(someModuleArtifacts));
    }

    @Test
    public void getAllArtifacts() {
        Set<ResolvedArtifact> someModuleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        Set<ResolvedArtifact> someChildModuleArtifacts = WrapUtil.toSet(createArtifact("someChildModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(someModuleArtifacts);

        Set<ResolvedArtifact> parent1SpecificArtifacts = WrapUtil.toSet(createArtifact("parent1Specific"));
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts);

        createAndAddParent("parent2", resolvedDependency, WrapUtil.toSet(createArtifact("parent2Specific")));

        DefaultResolvedDependency child = new DefaultResolvedDependency("someGroup", "someChild", "someVersion", "someChildConfiguration",
                someChildModuleArtifacts);
        resolvedDependency.getChildren().add(child);

        Set<ResolvedArtifact> childParent1SpecificArtifacts = WrapUtil.toSet(createArtifact("childParent1Specific"));
        createAndAddParent("childParent1", child, childParent1SpecificArtifacts);

        Set<ResolvedArtifact> childParent2SpecificArtifacts = WrapUtil.toSet(createArtifact("childParent2Specific"));
        createAndAddParent("childParent2", child, childParent2SpecificArtifacts);

        assertThat(resolvedDependency.getAllArtifacts(parentResolvedDependency1),
                equalTo(GUtil.addSets(someModuleArtifacts, parent1SpecificArtifacts, someChildModuleArtifacts, childParent1SpecificArtifacts, childParent2SpecificArtifacts)));
    }

    @Test
    public void equalsAndHashCode() {
        DefaultResolvedDependency dependency = new DefaultResolvedDependency("group", "name", "version", "config",
                Collections.EMPTY_SET);
        DefaultResolvedDependency same = new DefaultResolvedDependency("group", "name", "version", "config", Collections.EMPTY_SET);
        DefaultResolvedDependency differentGroup = new DefaultResolvedDependency("other", "name", "version", "config",
                Collections.EMPTY_SET);
        DefaultResolvedDependency differentName = new DefaultResolvedDependency("group", "other", "version", "config",
                Collections.EMPTY_SET);
        DefaultResolvedDependency differentVersion = new DefaultResolvedDependency("group", "name", "other", "config",
                Collections.EMPTY_SET);
        DefaultResolvedDependency differentConfiguration = new DefaultResolvedDependency("group", "name", "version",
                "other", Collections.EMPTY_SET);

        assertThat(dependency, strictlyEqual(same));
        assertThat(dependency, not(equalTo(differentGroup)));
        assertThat(dependency, not(equalTo(differentName)));
        assertThat(dependency, not(equalTo(differentVersion)));
        assertThat(dependency, not(equalTo(differentConfiguration)));
    }

    private DefaultResolvedDependency createAndAddParent(String parentName, DefaultResolvedDependency resolvedDependency, Set<ResolvedArtifact> parentSpecificArtifacts) {
        DefaultResolvedDependency parent = new DefaultResolvedDependency("someGroup", parentName, "someVersion", "someConfiguration",
                Collections.<ResolvedArtifact>emptySet());
        resolvedDependency.getParents().add(parent);
        resolvedDependency.addParentSpecificArtifacts(parent, parentSpecificArtifacts);
        return parent;
    }
}
