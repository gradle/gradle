/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ear

import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.AbstractArchiveTaskTest
import org.gradle.internal.file.PathToFileResolver
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor
import org.gradle.plugins.ear.descriptor.EarSecurityRole
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor
import org.gradle.test.fixtures.archive.JarTestFixture

class EarTest extends AbstractArchiveTaskTest {
    Ear ear
    PathToFileResolver nullPathToFileResolver = Stub(PathToFileResolver)

    def setup() {
        ear = createTask(Ear)
        configure(ear)
        // This would normally be set by the EarPlugin
        ear.libDirName = "lib"
    }

    @Override
    AbstractArchiveTask getArchiveTask() {
        ear
    }

    def "test Ear"() {
        expect:
        ear.archiveExtension.get() == Ear.EAR_EXTENSION
    }

    def "correct default deployment descriptor"() {
        when:
        ear.deploymentDescriptor = objectFactory.newInstance(DefaultDeploymentDescriptor, nullPathToFileResolver, objectFactory)
        def d = makeDeploymentDescriptor(ear)

        then:
        checkDeploymentDescriptor(d)
    }

    def "correct default deployment descriptor initialized from null"() {
        when:
        ear.deploymentDescriptor = null
        def d = makeDeploymentDescriptor(ear)

        then:
        checkDeploymentDescriptor(d)
    }

    def "can configure deployment descriptor using an Action"() {
        when:
        ear.deploymentDescriptor({ DeploymentDescriptor descriptor ->
            descriptor.applicationName = "myapp"
        } as Action<DeploymentDescriptor>)

        then:
        ear.deploymentDescriptor.applicationName.get() == "myapp"
    }

    def "can configure ear lib copyspec using an Action"() {
        given:
        ear.lib({ CopySpec spec ->
            spec.from temporaryFolder.createFile('file.txt')
        } as Action<CopySpec>)

        when:
        execute(ear)

        then:
        ear.archiveFile.get().asFile.isFile()
        new JarTestFixture(ear.archiveFile.get().asFile).assertContainsFile('lib/file.txt')
    }

    def "configures destinationDirectory for ear tasks"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.version = '1.0'

        then:
        def someEar = project.tasks.create('someEar', Ear)
        someEar.destinationDirectory.get().asFile == project.libsDirectory.get().asFile
    }

    private static DeploymentDescriptor makeDeploymentDescriptor(Ear e) {
        e.deploymentDescriptor {
            fileName = "myApp.xml"
            version = "5"
            applicationName = "myapp"
            initializeInOrder = true
            displayName = "My App"
            description = "My Application"
            libraryDirectory = "APP-INF/lib"
            module("my.jar", "java")
            webModule("my.war", "/")
            securityRole "admin"
            securityRole({ role ->
                role.roleName = "superadmin"
                role.description = "Super Admin Role"
            } as Action<EarSecurityRole>)

            withXml { provider ->
                //just adds an action
            }
        }
        return e.deploymentDescriptor
    }

    private static void checkDeploymentDescriptor(DeploymentDescriptor d) {
        assert d.fileName == "myApp.xml"
        assert d.version.get() == "5"
        assert d.applicationName.get() == "myapp"
        assert d.initializeInOrder.get()
        assert d.displayName.get() == "My App"
        assert d.description.get() == "My Application"
        assert d.libraryDirectory.get() == "APP-INF/lib"
        assert d.modules.get().size() == 2
        assert d.modules.get()[0].path.get() == "my.jar"
        assert d.modules.get()[1].path.get() == "my.war"
        assert d.modules.get()[1].contextRoot.get() == "/"
        assert d.moduleTypeMappings.get()["my.jar"] == "java"
        assert d.moduleTypeMappings.get()["my.war"] == "web"
        assert d.securityRoles.get().size() == 2
        assert d.securityRoles.get()[0].roleName.get() == "admin"
        assert d.securityRoles.get()[1].roleName.get() == "superadmin"
        assert d.securityRoles.get()[1].description.get() == "Super Admin Role"
        assert d.transformer.actions.size() == 1
    }
}
