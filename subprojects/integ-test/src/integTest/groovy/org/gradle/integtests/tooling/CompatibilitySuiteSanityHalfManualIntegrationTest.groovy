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

package org.gradle.integtests.tooling

import org.gradle.integtests.tooling.fixture.IncludeAllPermutations
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.Project
import spock.lang.Ignore
import spock.lang.Timeout

@MinToolingApiVersion('1.0-milestone-3')
@MinTargetGradleVersion('1.0-milestone-3')
@IncludeAllPermutations
@Ignore //run when needed
class CompatibilitySuiteSanityHalfManualIntegrationTest extends ToolingApiSpecification {

    def setup() {
        //useful for debugging:
        //toolingApi.isEmbedded = false
    }

    @Timeout(20)
    def "prints true versions used by provider and consumer"() {
        given:
        dist.file('build.gradle')  << """
description = GradleVersion.current().version
"""
        String message = 'True versions:\n';

        when:
        def model = withConnection {
            message += "consumer: " + it.getClass().getClassLoader().loadClass("org.gradle.util.GradleVersion").current().version.toString()
            it.getModel(Project)
        }
        message += "\nprovider: " + model.description.toString()

        then:
        //use your eyes and validate the output :)
        println '--------------\n' + message + '\n--------------'
    }
}