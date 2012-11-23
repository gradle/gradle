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
package org.gradle.api.publication.maven.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.GroovyMavenDeployer
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.internal.Actions
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.api.internal.ConfigureByMapAction
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.file.FileResolver
import spock.lang.Specification

class DefaultMavenRepositoryHandlerConventionTest extends Specification {
    final DefaultRepositoryHandler container = Mock()
    final FileResolver fileResolver = Mock()
    final ConfigurationContainer configurationContainer = Mock()
    final Conf2ScopeMappingContainer conf2ScopeMappingContainer = Mock()
    final DeployerFactory factory = Mock()
    final MavenPomMetaInfoProvider metaInfoProvider = Mock()
    final DefaultMavenRepositoryHandlerConvention convention = new DefaultMavenRepositoryHandlerConvention(container, factory)

    Action byMap(Map m) {
        new ConfigureByMapAction(m)
    }

    Action byClosure(Closure c) {
        new ClosureBackedAction(c)
    }

    Action composite(Action... actions) {
        Actions.composite(actions)
    }

    public void mavenDeployerWithoutName() {
        GroovyMavenDeployer deployer = Mock()

        when:
        def result = convention.mavenDeployer()

        then:
        result == deployer
        1 * factory.createMavenDeployer() >> deployer
        1 * container.addRepository(deployer, "mavenDeployer") >> deployer
    }

    public void mavenDeployerWithArgs() {
        GroovyMavenDeployer deployer = Mock()

        when:
        def result = convention.mavenDeployer(name: 'someName')

        then:
        result == deployer
        1 * factory.createMavenDeployer() >> deployer
        1 * container.addRepository(deployer, "mavenDeployer", byMap(name: 'someName')) >> deployer
    }

    public void mavenDeployerWithArgsAndClosure() {
        GroovyMavenDeployer deployer = Mock()
        def cl = {
            name = 'other'
        }

        when:
        def result = convention.mavenDeployer(name: 'someName', cl)

        then:
        result == deployer
        1 * factory.createMavenDeployer() >> deployer
        1 * container.addRepository(deployer, "mavenDeployer", composite(byMap(name: 'someName'), byClosure(cl))) >> deployer
    }

    public void mavenDeployerWithClosure() {
        GroovyMavenDeployer deployer = Mock()
        def cl = {
            name = 'other'
        }

        when:
        def result = convention.mavenDeployer(cl)

        then:
        result == deployer
        1 * factory.createMavenDeployer() >> deployer
        1 * container.addRepository(deployer, "mavenDeployer", byClosure(cl)) >> deployer
    }

    public void mavenInstallerWithoutName() {
        MavenResolver installer = Mock()

        when:
        def result = convention.mavenInstaller()

        then:
        result == installer
        1 * factory.createMavenInstaller() >> installer
        1 * container.addRepository(installer, "mavenInstaller") >> installer
    }

    public void mavenInstallerWithArgs() {
        MavenResolver installer = Mock()

        when:
        def result = convention.mavenInstaller(name: 'name')

        then:
        result == installer
        1 * factory.createMavenInstaller() >> installer
        1 * container.addRepository(installer, "mavenInstaller", byMap(name: 'name')) >> installer
    }

    public void mavenInstallerWithNameAndClosure() {
        MavenResolver installer = Mock()
        def cl = { name = 'other' }

        when:
        def result = convention.mavenInstaller(name: 'name', cl)

        then:
        result == installer
        1 * factory.createMavenInstaller() >> installer
        1 * container.addRepository(installer, "mavenInstaller", composite(byMap(name: 'name'), byClosure(cl))) >> installer
    }

    public void mavenInstallerWithClosure() {
        MavenResolver installer = Mock()
        def cl = { name = 'other' }

        when:
        def result = convention.mavenInstaller(cl)

        then:
        result == installer
        1 * factory.createMavenInstaller() >> installer
        1 * container.addRepository(installer, "mavenInstaller", byClosure(cl)) >> installer
    }

}
