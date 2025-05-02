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

/**
 * Tests {@link DistributionBasePlugin}.
 */
class DistributionBasePluginTest extends AbstractProjectBuilderSpec {

    def "provides default values for additional distributions"() {
        when:
        project.pluginManager.apply(DistributionBasePlugin)

        then:
        def distributions = project.extensions.getByType(DistributionContainer.class)
        def dist = distributions.create('custom')
        dist.name == 'custom'
        dist.distributionBaseName.get() == 'test-project-custom'
    }

    def "adds distZip task for custom distribution"() {
        when:
        project.pluginManager.apply(DistributionBasePlugin)
        project.distributions.create('custom')

        then:
        def task = project.tasks.customDistZip
        task instanceof Zip
        task.archiveFile.get().asFile == project.file("build/distributions/test-project-custom.zip")
    }

    def "adds distTar task for custom distribution"() {
        when:
        project.pluginManager.apply(DistributionBasePlugin)
        project.distributions.create('custom')

        then:
        def task = project.tasks.customDistTar
        task instanceof Tar
        task.archiveFile.get().asFile == project.file("build/distributions/test-project-custom.tar")
    }

    def "adds assembleDist task for custom distribution"() {
        when:
        project.pluginManager.apply(DistributionBasePlugin)
        project.distributions.create('custom')

        then:
        def task = project.tasks.assembleCustomDist
        task instanceof DefaultTask
        task TaskDependencyMatchers.dependsOn ("customDistZip","customDistTar")
    }

    def "adds installDist task for custom distribution"() {
        when:
        project.pluginManager.apply(DistributionBasePlugin)
        project.distributions.create('custom')

        then:
        def task = project.installCustomDist
        task instanceof Sync
        task.destinationDir == project.file("build/install/test-project-custom")
    }

}
