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

/**
 * @author Hans Dockter
 */
public class DefaultResolvedDependencyTest {
//    @Test
//    public void init() {
//        String someName = "someName";
//        String someConfiguration = "someConfiguration";
//        Set<File> someFiles = WrapUtil.toSet(new File("someFile"));
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency(someName, someConfiguration, getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, someFiles);
//        assertThat(resolvedDependency.getName(), equalTo(someName));
//        assertThat(resolvedDependency.getConfiguration(), equalTo(someConfiguration));
//        assertThat(resolvedDependency.getModuleFiles(), equalTo(someFiles));
//        assertThat(resolvedDependency.getChildren(), equalTo(Collections.<ResolvedDependency>emptySet()));
//        assertThat(resolvedDependency.getParents(), equalTo(Collections.<ResolvedDependency>emptySet()));
//    }
//
//    @Test
//    public void getAllModuleFiles() {
//        Set<File> someFiles = WrapUtil.toSet(new File("someFile"));
//        Set<File> someChildFiles = WrapUtil.toSet(new File("someFile"));
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someName", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, someFiles);
//        resolvedDependency.getChildren().add(new DefaultResolvedDependency("someChild", "someChildConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, someChildFiles));
//        assertThat(resolvedDependency.getAllModuleFiles(), equalTo(GUtil.addSets(someChildFiles, someFiles)));
//    }
//
//    @Test
//    public void getParentFiles() {
//        Set<File> someModuleFiles = WrapUtil.toSet(new File("someFile"));
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someName", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, someModuleFiles);
//
//        Set<File> parent1SpecificFiles = WrapUtil.toSet(new File("parent1Specific"));
//        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificFiles);
//
//        Set<File> parent2SpecificFiles = WrapUtil.toSet(new File("parent2Specific"));
//        DefaultResolvedDependency parentResolvedDependency2 = createAndAddParent("parent2", resolvedDependency, parent2SpecificFiles);
//
//        assertThat(resolvedDependency.getParentFiles(parentResolvedDependency1), equalTo(parent1SpecificFiles));
//        assertThat(resolvedDependency.getParentFiles(parentResolvedDependency2), equalTo(parent2SpecificFiles));
//    }
//
//    @Test
//    public void getFiles() {
//        Set<File> someModuleFiles = WrapUtil.toSet(new File("someModuleFile"));
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someName", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, someModuleFiles);
//
//        Set<File> parent1SpecificFiles = WrapUtil.toSet(new File("parent1Specific"));
//        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificFiles);
//
//        assertThat(resolvedDependency.getFiles(parentResolvedDependency1), equalTo(GUtil.addSets(someModuleFiles, parent1SpecificFiles)));
//    }
//
//    public void getFilesWithParentWithoutParentFiles() {
//        Set<File> moduleFiles = WrapUtil.toSet(new File("someModuleFile"));
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someName", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, moduleFiles);
//
//        DefaultResolvedDependency parent = new DefaultResolvedDependency("parent", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, Collections.<File>emptySet());
//        resolvedDependency.getParents().add(parent);
//        assertThat(resolvedDependency.getFiles(parent), equalTo(moduleFiles));
//    }
//
//    public void getParentFilesWithParentWithoutParentFiles() {
//        Set<File> moduleFiles = WrapUtil.toSet(new File("someModuleFile"));
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someName", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, moduleFiles);
//
//        DefaultResolvedDependency parent = new DefaultResolvedDependency("parent", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, Collections.<File>emptySet());
//        resolvedDependency.getParents().add(parent);
//        assertThat(resolvedDependency.getParentFiles(parent), equalTo(Collections.<File>emptySet()));
//    }
//
//    @Test(expected = InvalidUserDataException.class)
//    public void getParentFilesWithUnknownParent() {
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someName", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, Collections.<File>emptySet());
//        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency("parent2", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, Collections.<File>emptySet());
//        assertThat(resolvedDependency.getParentFiles(unknownParent),
//                equalTo(Collections.<File>emptySet()));
//    }
//
//    @Test(expected = InvalidUserDataException.class)
//    public void getFilesWithUnknownParent() {
//        Set<File> someModuleFiles = WrapUtil.toSet(new File("someModuleFile"));
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someName", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, Collections.<File>emptySet());
//
//        DefaultResolvedDependency unknownParent = new DefaultResolvedDependency("parent2", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, Collections.<File>emptySet());
//        assertThat(resolvedDependency.getParentFiles(unknownParent),
//                equalTo(someModuleFiles));
//    }
//
//    @Test
//    public void getAllFiles() {
//        Set<File> someModuleFiles = WrapUtil.toSet(new File("someModuleFile"));
//        Set<File> someChildModuleFiles = WrapUtil.toSet(new File("someChildModuleFile"));
//        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency("someName", "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, someModuleFiles);
//
//        Set<File> parent1SpecificFiles = WrapUtil.toSet(new File("parent1Specific"));
//        DefaultResolvedDependency parentResolvedDependency1 = createAndAddParent("parent1", resolvedDependency, parent1SpecificFiles);
//
//        createAndAddParent("parent2", resolvedDependency, WrapUtil.toSet(new File("parent2Specific")));
//
//        DefaultResolvedDependency child = new DefaultResolvedDependency("someChild", "someChildConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, someChildModuleFiles);
//        resolvedDependency.getChildren().add(child);
//
//        Set<File> childParent1SpecificFiles = WrapUtil.toSet(new File("childParent1Specific"));
//        createAndAddParent("childParent1", child, childParent1SpecificFiles);
//
//        Set<File> childParent2SpecificFiles = WrapUtil.toSet(new File("childParent2Specific"));
//        createAndAddParent("childParent2", child, childParent2SpecificFiles);
//
//        assertThat(resolvedDependency.getAllFiles(parentResolvedDependency1),
//                equalTo(GUtil.addSets(someModuleFiles, parent1SpecificFiles, someChildModuleFiles, childParent1SpecificFiles, childParent2SpecificFiles)));
//    }
//
//    private DefaultResolvedDependency createAndAddParent(String parentName, DefaultResolvedDependency resolvedDependency, Set<File> parentSpecificFiles) {
//        DefaultResolvedDependency parent = new DefaultResolvedDependency(parentName, "someConfiguration", getExtendedConfigurations(ivyNode, configuration), resolvedDependencies, Collections.<File>emptySet());
//        resolvedDependency.getParents().add(parent);
//        resolvedDependency.addParentSpecificFiles(parent, parentSpecificFiles);
//        return parent;
//    }
}
