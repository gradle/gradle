/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.distribution.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class DistributionPluginTest extends AbstractProjectBuilderSpec {
    def "adds convention object and a main distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)

        then:
        def distributions = project.extensions.getByType(DistributionContainer.class)
        def dist = distributions.main
        dist.name == 'main'
        dist.distributionBaseName.get() == 'test-project'
    }

    def "provides default values for additional distributions"() {
        when:
        project.pluginManager.apply(DistributionPlugin)

        then:
        def distributions = project.extensions.getByType(DistributionContainer.class)
        def dist = distributions.create('custom')
        dist.name == 'custom'
        dist.distributionBaseName.get() == 'test-project-custom'
    }

    def "adds distZip task for main distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)

        then:
        def task = project.tasks.distZip
        task instanceof Zip
        task.archiveFile.get().asFile == project.file("build/distributions/test-project.zip")
    }

    def "adds distZip task for custom distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)
        project.distributions.create('custom')

        then:
        def task = project.tasks.customDistZip
        task instanceof Zip
        task.archiveFile.get().asFile == project.file("build/distributions/test-project-custom.zip")
    }

    def "adds distTar task for main distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)

        then:
        def task = project.tasks.distTar
        task instanceof Tar
        task.archiveFile.get().asFile == project.file("build/distributions/test-project.tar")
    }

    def "adds distTar task for custom distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)
        project.distributions.create('custom')

        then:
        def task = project.tasks.customDistTar
        task instanceof Tar
        task.archiveFile.get().asFile == project.file("build/distributions/test-project-custom.tar")
    }

    def "adds assembleDist task for custom distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)
        project.distributions.create('custom')

        then:
        def task = project.tasks.assembleCustomDist
        task instanceof DefaultTask
        task TaskDependencyMatchers.dependsOn ("customDistZip","customDistTar")
    }

    def "distribution names include project version when specified"() {
        when:
        project.pluginManager.apply(DistributionPlugin)
        project.version = '1.2'

        then:
        def zip = project.tasks.distZip
        zip.archiveFile.get().asFile == project.file("build/distributions/test-project-1.2.zip")
        def tar = project.tasks.distTar
        tar.archiveFile.get().asFile == project.file("build/distributions/test-project-1.2.tar")
    }

    def "adds installDist task for main distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)

        then:
        def task = project.installDist
        task instanceof Sync
        task.destinationDir == project.file("build/install/test-project")
    }

    def "adds installDist task for custom distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)
        project.distributions.create('custom')

        then:
        def task = project.installCustomDist
        task instanceof Sync
        task.destinationDir == project.file("build/install/test-project-custom")
    }

    def "adds assembleDist task for main distribution"() {
        when:
        project.pluginManager.apply(DistributionPlugin)

        then:
        def task = project.assembleDist
        task.dependsOn.containsAll(["distTar", "distZip"])
    }

    def "distribution name is configurable"() {
        when:
        project.pluginManager.apply(DistributionPlugin)
        project.distributions.main.distributionBaseName = "SuperApp";

        then:
        def distZipTask = project.tasks.distZip
        distZipTask.archiveFileName.get() == "SuperApp.zip"
    }

    def "distribution names with classifier"() {
        when:
        project.pluginManager.apply(DistributionPlugin)
        project.distributions.main.distributionBaseName = "app"
        project.distributions.main.distributionClassifier = "classifier"

        then:
        def zip = project.tasks.distZip
        zip.archiveFileName.get() == "app-classifier.zip"
        def tar = project.tasks.distTar
        tar.archiveFileName.get() == "app-classifier.tar"
    }
}
