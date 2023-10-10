/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking

import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static java.util.Collections.emptySet
import static org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId

class DefaultDependencyLockingProviderTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    TestFile lockDir = tmpDir.createDir(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER)
    TestFile uniqueLockFile = tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME)

    FileResolver resolver = Mock()
    StartParameter startParameter = Mock()
    DomainObjectContext context = Mock()
    DependencySubstitutionRules dependencySubstitutionRules = Mock()
    FileResourceListener listener = Mock()
    PropertyFactory propertyFactory = new DefaultPropertyFactory(Stub(PropertyHost))
    FilePropertyFactory filePropertyFactory = TestFiles.filePropertyFactory()

    @Subject
    DefaultDependencyLockingProvider provider

    def setup() {
        context.identityPath(_) >> { String value -> Path.path(value) }
        context.getProjectPath() >> Path.path(':')
        resolver.canResolveRelativePath() >> true
        resolver.resolve(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER) >> lockDir
        resolver.resolve(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME) >> uniqueLockFile
        startParameter.getLockedDependenciesToUpdate() >> []
        provider = newProvider()
    }

    def 'can persist resolved modules as unique lockfile'() {
        given:
        tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME) << "empty=conf"
        startParameter.isWriteDependencyLocks() >> true
        provider = newProvider()
        def modules = [module('org', 'foo', '1.0'), module('org','bar','1.3')] as Set
        provider.loadLockState('conf')
        provider.persistResolvedDependencies('conf', modules, emptySet())

        when:
        provider.buildFinished()

        then:
        uniqueLockFile.text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
org:bar:1.3=conf
org:foo:1.0=conf
empty=
""".denormalize()

    }

    def 'can load lockfile as strict constraints (Unique: #unique)'() {
        given:
        writeLockFile(['org:bar:1.3', 'org:foo:1.0'], unique)

        when:
        def result = provider.loadLockState('conf')

        then:
        result.mustValidateLockState()
        result.getLockedDependencies() == [newId(DefaultModuleIdentifier.newId('org', 'bar'), '1.3'), newId(DefaultModuleIdentifier.newId('org', 'foo'), '1.0')] as Set

        1 * listener.fileObserved(_)
        if (!unique) {
            1 * listener.fileObserved(_)
        }

        where:
        unique << [true, false]
    }

    def 'can load lockfile as prefer constraints in update mode (Unique: #unique)'() {
        given:
        startParameter = Mock()
        startParameter.isWriteDependencyLocks() >> true
        startParameter.getLockedDependenciesToUpdate() >> ['org:foo']
        provider = newProvider()
        writeLockFile(['org:bar:1.3', 'org:foo:1.0'], unique)

        when:
        def result = provider.loadLockState('conf')

        then:
        !result.mustValidateLockState()
        result.getLockedDependencies() == [newId(DefaultModuleIdentifier.newId('org', 'bar'), '1.3')] as Set

        where:
        unique << [true, false]
    }

    def 'can filter lock entries using module update patterns (Unique: #unique)'() {
        given:
        startParameter = Mock()
        startParameter.isWriteDependencyLocks() >> true
        startParameter.getLockedDependenciesToUpdate() >> ['org:*']
        provider = newProvider()
        writeLockFile(['org:bar:1.3', 'org:foo:1.0'], unique)

        when:
        def result = provider.loadLockState('conf')

        then:
        !result.mustValidateLockState()
        result.getLockedDependencies() == [] as Set

        where:
        unique << [true, false]
    }

    def 'can filter lock entries using group update patterns (Unique: #unique)'() {
        given:
        startParameter = Mock()
        startParameter.isWriteDependencyLocks() >> true
        startParameter.getLockedDependenciesToUpdate() >> ['org.*:foo']
        provider = newProvider()
        writeLockFile(['org.bar:foo:1.3', 'com:foo:1.0'], unique)

        when:
        def result = provider.loadLockState('conf')

        then:
        !result.mustValidateLockState()
        result.getLockedDependencies() == [newId(DefaultModuleIdentifier.newId('com', 'foo'), '1.0')] as Set

        where:
        unique << [true, false]
    }

    def 'can filter lock entries impacted by dependency substitutions (Unique: #unique)'() {
        given:
        dependencySubstitutionRules.rulesMayAddProjectDependency() >> true
        Action< DependencySubstitution> substitutionAction = Mock()
        dependencySubstitutionRules.ruleAction >> substitutionAction
        substitutionAction.execute(_ as DependencySubstitution) >> { DependencySubstitution ds ->
            if (ds.requested.displayName.contains('foo')) {
                ds.useTarget(null)
            }
        }
        provider = newProvider()
        writeLockFile(['org:bar:1.1', 'org:foo:1.1'], unique)

        when:
        def result = provider.loadLockState('conf')

        then:
        result.getLockedDependencies() == [newId(DefaultModuleIdentifier.newId('org', 'bar'), '1.1')] as Set

        where:
        unique << [true, false]
    }

    def 'fails with invalid content in lock file (Unique: #unique)'() {
        given:
        writeLockFile(["invalid"], unique)

        when:
        provider.loadLockState('conf')

        then:
        def ex = thrown(InvalidLockFileException)
        1 * context.identityPath('conf') >> Path.path(':conf')
        ex.message == 'Invalid lock state for configuration \':conf\''
        ex.cause.message == 'The module notation does not respect the lock file format of \'group:name:version\' - received \'invalid\''

        where:
        unique << [true, false]
    }

    private ModuleComponentIdentifier module(String org, String name, String version) {
        return new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(org, name), version)
    }

    def 'fails with missing lockfile in strict mode (Unique: #unique)'() {
        given:
        provider.getLockMode().set(LockMode.STRICT)

        when:
        provider.loadLockState('conf')

        then:
        def ex = thrown(MissingLockStateException)
        1 * context.identityPath('conf') >> Path.path(':conf')
        ex.message == 'Locking strict mode: Configuration \':conf\' is locked but does not have lock state. To create the lock state, run a task that will resolve that configuration and add --write-locks'

        where:
        unique << [true, false]
    }

    def 'fails with invalid ignored dependencies notation #notation'() {
        uniqueLockFile << """
org:foo:1.0=conf
empty=
"""
        provider.ignoredDependencies.add(notation)

        when:
        provider.loadLockState('conf')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Ignored dependencies format must be <group>:<artifact> but '$invalid' is invalid."

        where:
        notation        | invalid
        'invalid'       | 'invalid'
        ',org:foo'      | ''
        'org:foo:1.0'   | 'org:foo:1.0'
        '*:*'           | '*:*'
    }

    def 'can drop lock state for no longer locked configuration'() {
        uniqueLockFile << """
org:foo:1.0=conf,otherConf
empty=
"""
        startParameter.isWriteDependencyLocks() >> true
        provider = newProvider()

        when:
        provider.confirmConfigurationNotLocked('conf')
        provider.buildFinished()

        then:
        uniqueLockFile.text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
org:foo:1.0=otherConf
empty=
""".denormalize()
    }

    private DefaultDependencyLockingProvider newProvider() {
        new DefaultDependencyLockingProvider(resolver, startParameter, context, dependencySubstitutionRules, propertyFactory, filePropertyFactory, listener)
    }

    def writeLockFile(List<String> modules, boolean unique = true, String configuration = 'conf') {
        if (unique) {
            tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME) << """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
${modules.toSorted().collect {"$it=$configuration"}.join('\n')}
empty=
""".denormalize()
        } else {
            lockDir.file("${configuration}.lockfile") << """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
${modules.toSorted().join('\n')}
""".denormalize()
        }
    }

}
