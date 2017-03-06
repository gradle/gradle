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
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Factory;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Sets.newHashSet;
import static org.gradle.util.Matchers.strictlyEqual;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class DefaultResolvedDependencyTest {
    private JUnit4Mockery context = new JUnit4GroovyMockery();

    @Test
    public void init() {
        String someGroup = "someGroup";
        String someName = "someName";
        String someVersion = "someVersion";
        String someConfiguration = "someConfiguration";
        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency(12L, newId(someGroup, someName, someVersion, someConfiguration));
        assertThat(resolvedDependency.getName(), equalTo(someGroup + ":" + someName + ":" + someVersion));
        assertThat(resolvedDependency.getModuleGroup(), equalTo(someGroup));
        assertThat(resolvedDependency.getModuleName(), equalTo(someName));
        assertThat(resolvedDependency.getModuleVersion(), equalTo(someVersion));
        assertThat(resolvedDependency.getConfiguration(), equalTo(someConfiguration));
        assertThat(resolvedDependency.getModuleArtifacts(), equalTo(Collections.<ResolvedArtifact>emptySet()));
        assertThat(resolvedDependency.getChildren(), equalTo(Collections.<ResolvedDependency>emptySet()));
        assertThat(resolvedDependency.getParents(), equalTo(Collections.<ResolvedDependency>emptySet()));
    }

    @Test
    public void getAllModuleArtifactsReturnsUnionOfAllIncomingArtifacts() {
        ResolvedArtifact artifact1 = createArtifact("1");
        ResolvedArtifact artifact2 = createArtifact("2");

        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency(12L, newId("someGroup", "someName", "someVersion", "someConfiguration"));

        DefaultResolvedDependency parent1 = new DefaultResolvedDependency(14L, newId("someGroup", "someChild", "someVersion", "p1"));
        parent1.addChild(resolvedDependency);

        DefaultResolvedDependency parent2 = new DefaultResolvedDependency(16L, newId("someGroup", "someChild", "someVersion", "p2"));
        parent2.addChild(resolvedDependency);

        resolvedDependency.addParentSpecificArtifacts(parent1, TestArtifactSet.create(ImmutableAttributes.EMPTY, Collections.singleton(artifact2)));
        resolvedDependency.addParentSpecificArtifacts(parent2, TestArtifactSet.create(ImmutableAttributes.EMPTY, Arrays.asList(artifact1, artifact2)));

        assertThat(resolvedDependency.getAllModuleArtifacts(), equalTo(toSet(artifact1, artifact2)));
    }

    @Test
    public void getParentArtifacts() {
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();

        Set<ResolvedArtifact> parent1SpecificArtifacts = toSet(createArtifact("parent1Specific"));
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts);

        Set<ResolvedArtifact> parent2SpecificArtifacts = toSet(createArtifact("parent2Specific"));
        DefaultResolvedDependency parentResolvedDependency2 = createAndAddParent("parent2", resolvedDependency, parent2SpecificArtifacts);

        assertThat(resolvedDependency.getParentArtifacts(parentResolvedDependency1), equalTo(parent1SpecificArtifacts));
        assertThat(resolvedDependency.getParentArtifacts(parentResolvedDependency2), equalTo(parent2SpecificArtifacts));
    }

    private ResolvedArtifact createArtifact(String name) {
        return createResolvedArtifact(context, name, "someType", "someExt", new File("pathTo" + name));
    }

    public static DefaultResolvedArtifact createResolvedArtifact(final Mockery context, final String name, final String type, final String extension, final File file) {
        final IvyArtifactName artifactStub = context.mock(IvyArtifactName.class, "artifact" + name);
        final ImmutableAttributesFactory factory = context.mock(ImmutableAttributesFactory.class);
        final BuildOperationExecutor buildOperationExecutor = context.mock(BuildOperationExecutor.class);
        context.checking(new Expectations() {{
            allowing(factory).builder(ImmutableAttributes.EMPTY);
            allowing(artifactStub).getName();
            will(returnValue(name));
            allowing(artifactStub).getType();
            will(returnValue(type));
            allowing(artifactStub).getExtension();
            will(returnValue(extension));
            allowing(artifactStub).getClassifier();
            will(returnValue(null));
        }});
        final Factory artifactSource = context.mock(Factory.class);
        context.checking(new Expectations() {{
            allowing(artifactSource).create();
            will(returnValue(file));
        }});
        final ResolvedDependency resolvedDependency = context.mock(ResolvedDependency.class);
        final ResolvedModuleVersion version = context.mock(ResolvedModuleVersion.class);
        context.checking(new Expectations() {{
            allowing(resolvedDependency).getModule();
            will(returnValue(version));
            allowing(version).getId();
            will(returnValue(new DefaultModuleVersionIdentifier("group", name, "1.2")));
        }});
        return new DefaultResolvedArtifact(resolvedDependency.getModule().getId(), artifactStub, context.mock(ComponentArtifactIdentifier.class), context.mock(TaskDependency.class), artifactSource);
    }

    private DefaultResolvedDependency createResolvedDependency() {
        return new DefaultResolvedDependency(12L, newId("someGroup", "someName", "someVersion", "someConfiguration"));
    }

    @Test
    public void getArtifacts() {
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();

        Set<ResolvedArtifact> parent1SpecificArtifacts = toSet(createArtifact("parent1Specific"));
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts);

        assertThat(resolvedDependency.getArtifacts(parentResolvedDependency1), equalTo(parent1SpecificArtifacts));
    }

    @Test
    public void getArtifactsWithParentWithoutParentArtifacts() {
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();

        DefaultResolvedDependency parent = new DefaultResolvedDependency(10L, newId("someGroup", "parent", "someVersion", "someConfiguration"));
        resolvedDependency.getParents().add(parent);
        assertThat(resolvedDependency.getArtifacts(parent), equalTo(Collections.<ResolvedArtifact>emptySet()));
    }

    @Test
    public void getParentArtifactsWithParentWithoutParentArtifacts() {
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();

        DefaultResolvedDependency parent = new DefaultResolvedDependency(10L, newId("someGroup", "parent", "someVersion", "someConfiguration"));
        resolvedDependency.getParents().add(parent);
        assertThat(resolvedDependency.getParentArtifacts(parent), equalTo(Collections.<ResolvedArtifact>emptySet()));
    }

    @Test(expected = InvalidUserDataException.class)
    public void getParentArtifactsWithUnknownParent() {
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();
        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency(10L, newId("someGroup", "parent2", "someVersion", "someConfiguration"));
        assertThat(resolvedDependency.getParentArtifacts(unknownParent),
                equalTo(Collections.<ResolvedArtifact>emptySet()));
    }

    @Test(expected = InvalidUserDataException.class)
    public void getArtifactsWithUnknownParent() {
        Set<ResolvedArtifact> someModuleArtifacts = toSet(createArtifact("someModuleResolvedArtifact"));
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();

        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency(10L, newId("someGroup", "parent2", "someVersion", "someConfiguration"));
        assertThat(resolvedDependency.getParentArtifacts(unknownParent),
                equalTo(someModuleArtifacts));
    }

    @Test
    public void getAllArtifacts() {
        DefaultResolvedDependency resolvedDependency = createResolvedDependency();

        Set<ResolvedArtifact> parent1SpecificArtifacts = newHashSet(createArtifact("parent1Specific"));
        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificArtifacts);

        createAndAddParent("parent2", resolvedDependency, newHashSet(createArtifact("parent2Specific")));

        DefaultResolvedDependency child = new DefaultResolvedDependency(10L, newId("someGroup", "someChild", "someVersion", "someChildConfiguration"));
        resolvedDependency.addChild(child);

        Set<ResolvedArtifact> childParent1SpecificArtifacts = newHashSet(createArtifact("childParent1Specific"));
        createAndAddParent("childParent1", child, childParent1SpecificArtifacts);

        Set<ResolvedArtifact> childParent2SpecificArtifacts = newHashSet(createArtifact("childParent2Specific"));
        createAndAddParent("childParent2", child, childParent2SpecificArtifacts);

        Iterable<ResolvedArtifact> allArtifacts = newHashSet(concat(parent1SpecificArtifacts, childParent1SpecificArtifacts, childParent2SpecificArtifacts));
        assertThat(resolvedDependency.getAllArtifacts(parentResolvedDependency1), equalTo(allArtifacts));
    }

    @Test
    public void equalsAndHashCode() {
        DefaultResolvedDependency dependency = new DefaultResolvedDependency(1L, newId("group", "name", "version", "config"));
        DefaultResolvedDependency same = new DefaultResolvedDependency(1L, newId("group", "name", "version", "config"));
        DefaultResolvedDependency differentGroup = new DefaultResolvedDependency(1L, newId("other", "name", "version", "config"));
        DefaultResolvedDependency differentName = new DefaultResolvedDependency(1L, newId("group", "other", "version", "config"));
        DefaultResolvedDependency differentVersion = new DefaultResolvedDependency(1L, newId("group", "name", "other", "config"));
        DefaultResolvedDependency differentConfiguration = new DefaultResolvedDependency(1L, newId("group", "name", "version", "other"));

        assertThat(dependency, strictlyEqual(same));
        assertThat(dependency, not(equalTo(differentGroup)));
        assertThat(dependency, not(equalTo(differentName)));
        assertThat(dependency, not(equalTo(differentVersion)));
        assertThat(dependency, not(equalTo(differentConfiguration)));
    }

    private DefaultResolvedDependency createAndAddParent(String parentName, DefaultResolvedDependency resolvedDependency, Set<ResolvedArtifact> parentSpecificArtifacts) {
        DefaultResolvedDependency parent = new DefaultResolvedDependency(10L, newId("someGroup", parentName, "someVersion", "someConfiguration"));
        resolvedDependency.getParents().add(parent);
        resolvedDependency.addParentSpecificArtifacts(parent, TestArtifactSet.create(ImmutableAttributes.EMPTY, parentSpecificArtifacts));
        return parent;
    }

    public static ResolvedConfigurationIdentifier newId(String group, String name, String version, String config) {
        return new ResolvedConfigurationIdentifier(new DefaultModuleVersionIdentifier(group, name, version), config);
    }

}
