/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.buildinit.plugins.AbstractInitIntegrationSpec
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl

import static org.gradle.buildinit.tasks.InitBuild.DEFAULT_JAVA_VERSION;

class DaemonJvmCriteriaGeneratorIntegrationTest extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { null }

    def "Given gradle init run generating JVM criteria When assert it with updateDaemonJvm task run with same version and adoptium vendor Then files properties matches"() {
        given:
        run "init"
        assertDaemonJvmCriteriaGenerated()
        def initializedDaemonJvmCriteria = daemonJvmCriteriaFile.text
        assert daemonJvmCriteriaFile.delete()

        when:
        dslFixtureFor(BuildInitDsl.KOTLIN).getSettingsFile() << """
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention") version "+"
            }
        """
        run "updateDaemonJvm", "--jvm-version=${DEFAULT_JAVA_VERSION}"

        then:
        def expectedDaemonJvmCriteria = daemonJvmCriteriaFile.text
        assert expectedDaemonJvmCriteria == initializedDaemonJvmCriteria
    }
}
