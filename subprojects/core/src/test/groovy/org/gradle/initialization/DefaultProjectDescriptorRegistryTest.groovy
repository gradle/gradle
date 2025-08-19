/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.initialization

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Path
import spock.lang.Specification

class DefaultProjectDescriptorRegistryTest extends Specification {

    public static final String CHILD_NAME = "child"
    public static final String CHILD_CHILD_NAME = "childchild"

    private static final TestFile TEST_DIR = new TestFile("testDir")
    private static final FileResolver FILE_RESOLVER = TestFiles.resolver(TEST_DIR.getAbsoluteFile())
    private final DefaultProjectDescriptorRegistry registry = new DefaultProjectDescriptorRegistry()

    def "can add root project"() {
        when:
        ProjectDescriptorInternal root = descriptor("root")

        then:
        registry.getRootProject().is(root)
    }

    def addProject() {
        when:
        ProjectDescriptorInternal root = descriptor("root")

        then:
        root.is(registry.getProject(root.getPath()))
        registry.getAllProjects().contains(root)

        when:
        ProjectDescriptorInternal child = descriptor(CHILD_NAME, root)

        then:
        child.is(registry.getProject(child.getPath()))
        registry.getAllProjects().contains(child)

        when:
        ProjectDescriptorInternal childChild = descriptor(CHILD_CHILD_NAME, child)

        then:
        childChild.is(registry.getProject(childChild.getPath()))
        registry.getAllProjects().contains(childChild)
    }

    def accessMethodsForNonexistentsPaths() {
        expect:
        registry.getProject(Path.ROOT.path) == null
    }

    def canLocalAllProjects() {
        ProjectDescriptorInternal root = descriptor("root")
        ProjectDescriptorInternal child = descriptor(CHILD_NAME, root)
        ProjectDescriptorInternal childChild = descriptor(CHILD_CHILD_NAME, child)

        expect:
        registry.getAllProjects() == ([root, child, childChild] as Set)
    }

    def canRemoveProject() {
        ProjectDescriptorInternal root = descriptor("root")
        ProjectDescriptorInternal child = descriptor(CHILD_NAME, root)
        ProjectDescriptorInternal childChild = descriptor(CHILD_CHILD_NAME, child)

        when:
        String path = childChild.getPath()

        then:
        registry.removeProject(path).is(childChild)
        registry.getProject(path) == null
        !registry.getAllProjects().contains(childChild)
    }

    def "can add project"() {
        ProjectDescriptorInternal rootProject = descriptor("testName")

        expect:
        rootProject.is(registry.getProject(rootProject.getPath()))
    }

    def changeProjectDescriptorPath() {
        // Project is added as a side effect
        ProjectDescriptorInternal project = descriptor("name")
        registry.changeDescriptorPath(Path.path(":"), Path.path(":newPath"))

        expect:
        registry.getRootProject() == null
        registry.getProject(":") == null
        registry.getProject(":newPath") is project
    }

    private ProjectDescriptorInternal descriptor(String name, ProjectDescriptorInternal parent = null, TestFile projectDir = TEST_DIR.file(name)) {
        // Project is added to the registry as a side effect (yuck)
        return new DefaultProjectDescriptor(parent, name, projectDir, registry, FILE_RESOLVER)
    }

}
