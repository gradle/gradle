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

import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.Tar
import org.gradle.util.HelperUtil
import spock.lang.Specification

class DistributionPluginTest extends Specification {
    private final Project project = HelperUtil.builder().withName("test-project").build()

    def "adds convention object and a main distribution"() {
        when:
        project.apply(plugin: DistributionPlugin)

        then:
        def distributions = project.extensions.getByType(DistributionContainer.class)
        def dist = distributions.main
        dist.name == 'main'
        dist.baseName == 'test-project'
    }

    def "provides default values for additional distributions"() {
        when:
        project.apply(plugin: DistributionPlugin)

        then:
        def distributions = project.extensions.getByType(DistributionContainer.class)
        def dist = distributions.create('custom')
        dist.name == 'custom'
        dist.baseName == 'test-project-custom'
    }

    def "adds distZip task for main distribution"() {
        when:
        project.apply(plugin: DistributionPlugin)

        then:
        def task = project.tasks.distZip
        task instanceof Zip
        task.archiveName == "test-project.zip"
    }

    def "adds distZip task for custom distribution"() {
        when:
        project.apply(plugin: DistributionPlugin)
        project.distributions.create('custom')

        then:
        def task = project.tasks.customDistZip
        task instanceof Zip
        task.archiveName == "test-project-custom.zip"
    }

    def "adds distTar task for main distribution"() {
        when:
        project.apply(plugin: DistributionPlugin)

        then:
        def task = project.tasks.distTar
        task instanceof Tar
        task.archiveName == "test-project.tar"
    }

    def "adds distTar task for custom distribution"() {
        when:
        project.apply(plugin: DistributionPlugin)
        project.distributions.create('custom')

        then:
        def task = project.tasks.customDistTar
        task instanceof Tar
        task.archiveName == "test-project-custom.tar"
    }

    def "adds installDist task for main distribution"() {
        when:
        project.apply(plugin: DistributionPlugin)

        then:
        def task = project.installDist
        task instanceof Sync
        task.destinationDir == project.file("build/install/test-project")
    }

    def "adds installDist task for custom distribution"() {
        when:
        project.apply(plugin: DistributionPlugin)
        project.distributions.create('custom')

        then:
        def task = project.installCustomDist
        task instanceof Sync
        task.destinationDir == project.file("build/install/test-project-custom")
    }

    public void "distribution name is configurable"() {
        when:
        project.apply(plugin: DistributionPlugin)
        project.distributions.main.baseName = "SuperApp";

        then:
        def distZipTask = project.tasks.distZip
        distZipTask.archiveName == "SuperApp.zip"
    }
}
