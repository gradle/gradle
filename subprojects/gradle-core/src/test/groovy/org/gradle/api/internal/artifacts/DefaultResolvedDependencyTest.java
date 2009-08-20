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
package org.gradle.api.internal.artifacts;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedDependencyTest {
    private static final Set<String> SOME_CONFIGURATION_HIERARCHY = WrapUtil.toSet("someConfiguration", "conf1");

    @Test
    public void init() {
        String someGroup = "someGroup";
        String someName = "someName";
        String someVersion = "someVersion";
        String someConfiguration = "someConfiguration";
        Set<ResolvedArtifact> someArtifacts = WrapUtil.<ResolvedArtifact>toSet(createArtifact("someName"));
        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency(someGroup, someName, someVersion, someConfiguration, SOME_CONFIGURATION_HIERARCHY, someArtifacts);
        assertThat(resolvedDependency.getGroup(), equalTo(someGroup));
        assertThat(resolvedDependency.getName(), equalTo(someName));
        assertThat(resolvedDependency.getVersion(), equalTo(someVersion));
        assertThat(resolvedDependency.getConfiguration(), equalTo(someConfiguration));
        assertThat(resolvedDependency.getModuleArtifacts(), equalTo(someArtifacts));
        assertThat(resolvedDependency.getConfigurationHierarchy(), equalTo(SOME_CONFIGURATION_HIERARCHY));
        assertThat(resolvedDependency.getChildren(), equalTo(Collections.<ResolvedDependency>emptySet()));
        assertThat(resolvedDependency.getParents(), equalTo(Collections.<ResolvedDependency>emptySet()));
    }

    @Test
    public void getAllModuleArtifacts() {
        Set<ResolvedArtifact> someModuleArtifacts = WrapUtil.<ResolvedArtifact>toSet(createArtifact("moduleArtifact"));
        Set<ResolvedArtifact> someChildModuleArtifacts = WrapUtil.<ResolvedArtifact>toSet(createArtifact("childModuleArtifact"));
        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someGroup", "someName", "someVersion", "someConfiguration", SOME_CONFIGURATION_HIERARCHY, someModuleArtifacts);
        resolvedDependency.getChildren().add(new DefaultResolvedDependency("someGroup", "someChild", "someVersion", "someChildConfiguration", SOME_CONFIGURATION_HIERARCHY, someChildModuleArtifacts));
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
        return new DefaultResolvedArtifact(name, "someType", "someExt", new File("pathTo" + name));
    }

    private DefaultResolvedDependency createResolvedDependency(Set<ResolvedArtifact> moduleArtifacts) {
        return new DefaultResolvedDependency("someGroup", "someName", "someVersion", "someConfiguration", SOME_CONFIGURATION_HIERARCHY, moduleArtifacts);
    }

    @Test
    public void getArtifacts() {
        Set<ResolvedArtifact> someModuleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(someModuleArtifacts);

        Set<ResolvedArtifact> parent1SpecificArtifacts = WrapUtil.toSet(createArtifact("parent1Specific"));
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts);

        assertThat(resolvedDependency.getArtifacts(parentResolvedDependency1), equalTo(GUtil.addSets(someModuleArtifacts, parent1SpecificArtifacts)));
    }

    public void getArtifactsWithParentWithoutParentArtifacts() {
        Set<ResolvedArtifact> moduleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(moduleArtifacts);

        DefaultResolvedDependency parent = new DefaultResolvedDependency("someGroup", "parent", "someVersion", "someConfiguration", SOME_CONFIGURATION_HIERARCHY, Collections.<ResolvedArtifact>emptySet());
        resolvedDependency.getParents().add(parent);
        assertThat(resolvedDependency.getArtifacts(parent), equalTo(moduleArtifacts));
    }

    public void getParentArtifactsWithParentWithoutParentArtifacts() {
        Set<ResolvedArtifact> moduleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(moduleArtifacts);

        DefaultResolvedDependency parent = new DefaultResolvedDependency("someGroup", "parent", "someVersion", "someConfiguration", SOME_CONFIGURATION_HIERARCHY, Collections.<ResolvedArtifact>emptySet());
        resolvedDependency.getParents().add(parent);
        assertThat(resolvedDependency.getParentArtifacts(parent), equalTo(Collections.<ResolvedArtifact>emptySet()));
    }

    @Test(expected = InvalidUserDataException.class)
    public void getParentArtifactsWithUnknownParent() {
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(Collections.<ResolvedArtifact>emptySet());
        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency("someGroup", "parent2", "someVersion", "someConfiguration", SOME_CONFIGURATION_HIERARCHY, Collections.<ResolvedArtifact>emptySet());
        assertThat(resolvedDependency.getParentArtifacts(unknownParent),
                equalTo(Collections.<ResolvedArtifact>emptySet()));
    }

    @Test(expected = InvalidUserDataException.class)
    public void getArtifactsWithUnknownParent() {
        Set<ResolvedArtifact> someModuleArtifacts = WrapUtil.toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency(Collections.<ResolvedArtifact>emptySet());

        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency("someGroup", "parent2", "someVersion", "someConfiguration", SOME_CONFIGURATION_HIERARCHY, Collections.<ResolvedArtifact>emptySet());
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

        DefaultResolvedDependency child = new DefaultResolvedDependency("someGroup", "someChild", "someVersion", "someChildConfiguration", SOME_CONFIGURATION_HIERARCHY, someChildModuleArtifacts);
        resolvedDependency.getChildren().add(child);

        Set<ResolvedArtifact> childParent1SpecificArtifacts = WrapUtil.toSet(createArtifact("childParent1Specific"));
        createAndAddParent("childParent1", child, childParent1SpecificArtifacts);

        Set<ResolvedArtifact> childParent2SpecificArtifacts = WrapUtil.toSet(createArtifact("childParent2Specific"));
        createAndAddParent("childParent2", child, childParent2SpecificArtifacts);

        assertThat(resolvedDependency.getAllArtifacts(parentResolvedDependency1),
                equalTo(GUtil.addSets(someModuleArtifacts, parent1SpecificArtifacts, someChildModuleArtifacts, childParent1SpecificArtifacts, childParent2SpecificArtifacts)));
    }

    private DefaultResolvedDependency createAndAddParent(String parentName, DefaultResolvedDependency resolvedDependency, Set<ResolvedArtifact> parentSpecificArtifacts) {
        DefaultResolvedDependency parent = new DefaultResolvedDependency("someGroup", parentName, "someVersion", "someConfiguration", SOME_CONFIGURATION_HIERARCHY, Collections.<ResolvedArtifact>emptySet());
        resolvedDependency.getParents().add(parent);
        resolvedDependency.addParentSpecificArtifacts(parent, parentSpecificArtifacts);
        return parent;
    }
}
