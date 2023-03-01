/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

class DefaultProjectDependencyConstraintTest extends Specification {

    @Issue("https://github.com/gradle/gradle/issues/17179")
    def "can copy project dependency constraint"() {
        setup:
        DefaultProjectDependencyConstraint constraint = createProjectDependencyConstraint()
        constraint.force = true
        constraint.reason = "reason"

        when:
        def constraintCopy = constraint.copy()

        then:
        constraintCopy.group == constraint.group
        constraintCopy.name == constraint.name
        constraintCopy.version == constraint.version
        constraintCopy.versionConstraint == constraint.versionConstraint
        constraintCopy.versionConstraint.preferredVersion == constraint.versionConstraint.preferredVersion
        constraintCopy.versionConstraint.rejectedVersions == constraint.versionConstraint.rejectedVersions
        constraintCopy.reason == constraint.reason
        constraintCopy.force == constraint.force
    }

    @Issue("https://github.com/gradle/gradle/issues/17179")
    def "modifying mutable properties on the copy does not affect the original"() {
        setup:
        DefaultProjectDependencyConstraint constraint = createProjectDependencyConstraint()
        constraint.force = false
        constraint.reason = 'reason1'
        DefaultProjectDependencyConstraint constraintCopy = constraint.copy()

        when:
        constraintCopy.force = true
        constraintCopy.reason = 'reason2'

        then:
        constraint.force == false
        constraint.reason == 'reason1'
    }

    private DefaultProjectDependencyConstraint createProjectDependencyConstraint() {
        def project = Mock(ProjectInternal) {
            getGroup() >> "org.example"
            getVersion() >> "0.0.1"
        }
        def dependencyFactory = new DefaultProjectDependencyFactory(
                TestUtil.instantiatorFactory().decorateLenient(),
                true,
                new CapabilityNotationParserFactory(false).create(),
                AttributeTestUtil.attributesFactory(),
                TestFiles.taskDependencyFactory()
        )
        def projectDependency = dependencyFactory.create(project, "mockConfiguration")
        new DefaultProjectDependencyConstraint(projectDependency)
    }
}
