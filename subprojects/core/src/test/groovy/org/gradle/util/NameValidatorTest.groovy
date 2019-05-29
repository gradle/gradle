/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.util

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeContainer
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TaskFactory
import org.gradle.api.internal.project.taskfactory.TaskInstantiator
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.nativeplatform.internal.DefaultFlavorContainer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(NameValidator)
class NameValidatorTest extends Specification {
    static forbiddenCharacters = NameValidator.FORBIDDEN_CHARACTERS
    static forbiddenLeadingAndTrailingCharacter = NameValidator.FORBIDDEN_LEADING_AND_TRAILING_CHARACTER
    static invalidNames = forbiddenCharacters.collect { "a${it}b" } + ["${forbiddenLeadingAndTrailingCharacter}ab", "ab${forbiddenLeadingAndTrailingCharacter}", '']

    @Shared
    def domainObjectContainersWithValidation = [
            ["artifact types", new DefaultArtifactTypeContainer(TestUtil.instantiatorFactory().decorateLenient(), AttributeTestUtil.attributesFactory(), CollectionCallbackActionDecorator.NOOP)],
            ["configurations", new DefaultConfigurationContainer(null, TestUtil.instantiatorFactory().decorateLenient(), domainObjectContext(), Mock(ListenerManager), null, null, null, null, Mock(FileCollectionFactory), null, null, null, null, null, AttributeTestUtil.attributesFactory(), null, null, null, null, Stub(DocumentationRegistry), CollectionCallbackActionDecorator.NOOP, null, TestUtil.domainObjectCollectionFactory())],
            ["flavors", new DefaultFlavorContainer(TestUtil.instantiatorFactory().decorateLenient(), CollectionCallbackActionDecorator.NOOP)],
            ["source sets", new DefaultSourceSetContainer(TestFiles.resolver(), null, TestUtil.instantiatorFactory().decorateLenient(), TestUtil.objectFactory(), CollectionCallbackActionDecorator.NOOP)]
    ]

    def cleanup() {
        SingleMessageLogger.reset()
    }

    @Unroll
    def "tasks are not allowed to be named '#name'"() {
        when:
        def project = Mock(ProjectInternal) {
            projectPath(_) >> Path.path(":foo:bar")
            identityPath(_) >> Path.path("build:foo:bar")
            getGradle() >> Mock(GradleInternal) {
                getIdentityPath() >> Path.path(":build:foo:bar")
            }
        }
        new TaskInstantiator(new TaskFactory(project, Mock(InstantiationScheme)), project).create(name, DefaultTask)

        then:
        def exception = thrown(InvalidUserDataException)
        assertForbidden(name, exception.getMessage())

        where:
        name << invalidNames
    }

    @Unroll
    def "#objectType are not allowed to be named '#name'"() {
        when:
        domainObjectContainer.create(name)

        then:
        def exception = thrown(InvalidUserDataException)
        assertForbidden(name, exception.getMessage())

        where:
        [name, objectType, domainObjectContainer] << [invalidNames, domainObjectContainersWithValidation].combinations().collect { [it[0], it[1][0], it[1][1]] }
    }

    def "names are not allowed to be empty"() {
        given:
        def name= ''

        when:
        NameValidator.validate('', 'name', '')

        then:
        def exception = thrown(InvalidUserDataException)
        assertForbidden(name, exception.getMessage())
    }

    private DomainObjectContext domainObjectContext() {
        def mock = Mock(DomainObjectContext)
        mock.projectPath(_) >> Mock(Path)
        mock
    }

    void assertForbidden(name, message) {
        if (name == '') {
            assert message.contains("must not be empty.")
        } else if (name.contains("" + forbiddenLeadingAndTrailingCharacter)) {
            assert message.contains("' must not start or end with a '.'.")
        } else {
            assert message.contains("""' must not contain any of the following characters: [/, \\, :, <, >, ", ?, *, |].""")
        }
    }
}
