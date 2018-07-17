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
package org.gradle.api.internal.tasks

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.featurelifecycle.FeatureUsage
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.util.NameValidator
import org.gradle.util.SingleMessageLogger
import spock.lang.Specification
import spock.lang.Unroll

class DefaultSourceSetContainerTest extends Specification {
    static forbiddenCharacters = NameValidator.FORBIDDEN_CHARACTERS
    static forbiddenLeadingAndTrailingCharacter = NameValidator.FORBIDDEN_LEADING_AND_TRAILING_CHARACTER
    static invalidNames = forbiddenCharacters.collect { "a${it}b"} + ["${forbiddenLeadingAndTrailingCharacter}ab", "ab${forbiddenLeadingAndTrailingCharacter}", '']

    private final DefaultSourceSetContainer container = new DefaultSourceSetContainer(TestFiles.resolver(), null, DirectInstantiator.INSTANCE, TestFiles.sourceDirectorySetFactory())

    def "can create a source set"() {
        when:
        SourceSet set = container.create("main")

        then:
        set instanceof DefaultSourceSet
        set.name == "main"
    }

    @Unroll
    def "source sets are not allowed to be named '#name'"() {
        given:
        def loggingDeprecatedFeatureHandler = Mock(LoggingDeprecatedFeatureHandler)
        SingleMessageLogger.deprecatedFeatureHandler = loggingDeprecatedFeatureHandler

        when:
        container.create(name)

        then:
        1 * loggingDeprecatedFeatureHandler.featureUsed(_  as FeatureUsage) >> { FeatureUsage usage ->
            assertForbidden(name, usage.formattedMessage())
        }

        cleanup:
        SingleMessageLogger.reset()

        where:
        name << invalidNames
    }

    void assertForbidden(name, message) {
        if (name == '') {
            assert message == "The name is empty. This has been deprecated and is scheduled to be removed in Gradle 5.0."
        } else if (name.contains("" + forbiddenLeadingAndTrailingCharacter)) {
            assert message == """The name '${name}' starts or ends with a '.'. This has been deprecated and is scheduled to be removed in Gradle 5.0."""
        } else {
            assert message == """The name '${name}' contains at least one of the following characters: [ , /, \\, :, <, >, ", ?, *, |]. This has been deprecated and is scheduled to be removed in Gradle 5.0."""
        }
    }
}
