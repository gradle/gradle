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

import org.gradle.api.artifacts.maven.GroovyMavenDeployer
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.MavenRepositoryHandlerConvention
import spock.lang.Specification

import static org.gradle.api.reflect.TypeOf.typeOf

class DefaultMavenRepositoryHandlerConventionTest extends Specification {
    final DefaultRepositoryHandler container = Mock()
    final FileResolver fileResolver = Mock()
    final DeployerFactory factory = Mock()
    final DefaultMavenRepositoryHandlerConvention convention = new DefaultMavenRepositoryHandlerConvention(container, factory)

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
        1 * container.addRepository(deployer, "mavenDeployer", _) >> { def d, def name, def action -> action.execute(d); d }
        1 * deployer.setName("someName")
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
        1 * container.addRepository(deployer, "mavenDeployer", _) >> { def d, def name, def action -> action.execute(d); d }
        1 * deployer.setName("someName")
        1 * deployer.setName("other")
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
        1 * container.addRepository(deployer, "mavenDeployer", _) >> { def d, def name, def action -> action.execute(d); d }
        1 * deployer.setName("other")
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
        1 * container.addRepository(installer, "mavenInstaller", _) >> { def d, def name, def action -> action.execute(d); d }
        1 * installer.setName("name")
    }

    public void mavenInstallerWithNameAndClosure() {
        MavenResolver installer = Mock()
        def cl = { name = 'other' }

        when:
        def result = convention.mavenInstaller(name: 'name', cl)

        then:
        result == installer
        1 * factory.createMavenInstaller() >> installer
        1 * container.addRepository(installer, "mavenInstaller", _) >> { def d, def name, def action -> action.execute(d); d }
        1 * installer.setName("name")
        1 * installer.setName("other")
    }

    public void mavenInstallerWithClosure() {
        MavenResolver installer = Mock()
        def cl = { name = 'other' }

        when:
        def result = convention.mavenInstaller(cl)

        then:
        result == installer
        1 * factory.createMavenInstaller() >> installer
        1 * container.addRepository(installer, "mavenInstaller", _) >> { def d, def name, def action -> action.execute(d); d }
        1 * installer.setName("other")
    }

    void exposesConventionPublicType() {
        convention.publicType == typeOf(MavenRepositoryHandlerConvention)
    }
}
