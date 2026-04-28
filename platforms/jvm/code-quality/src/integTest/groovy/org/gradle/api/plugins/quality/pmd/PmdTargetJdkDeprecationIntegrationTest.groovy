/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.plugins.quality.pmd

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PmdTargetJdkDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String UPGRADE_GUIDE_URL =
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_pmd_target_jdk"

    private static final String PMD_EXTENSION_DEPRECATION = "The PmdExtension.setTargetJdk(TargetJdk) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. " +
        "This property is a no-op for PMD 5.0 and later, which infer the language version from the rule sets. " +
        "Remove the targetJdk configuration from your build. " +
        "Consult the upgrading guide for further information: " + UPGRADE_GUIDE_URL

    private static final String PMD_TASK_DEPRECATION = "The Pmd.setTargetJdk(TargetJdk) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. " +
        "This property is a no-op for PMD 5.0 and later, which infer the language version from the rule sets. " +
        "Remove the targetJdk configuration from your build. " +
        "Consult the upgrading guide for further information: " + UPGRADE_GUIDE_URL

    private static final String TARGET_JDK_TYPE_DEPRECATION = "The org.gradle.api.plugins.quality.TargetJdk type has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. " +
        "Consult the upgrading guide for further information: " + UPGRADE_GUIDE_URL

    private static final String PMD_EXTENSION_OBJECT_DEPRECATION = "The PmdExtension.setTargetJdk(Object) method has been deprecated. " +
        "This is scheduled to be removed in Gradle 10. " +
        "This property is a no-op for PMD 5.0 and later, which infer the language version from the rule sets. " +
        "Remove the targetJdk configuration from your build. " +
        "Consult the upgrading guide for further information: " + UPGRADE_GUIDE_URL

    def setup() {
        buildFile << """
            plugins {
                id("java")
                id("pmd")
            }

            ${mavenCentralRepository()}
        """
    }

    def "setting PmdExtension.targetJdk emits a deprecation warning"() {
        given:
        buildFile << """
            pmd {
                targetJdk = TargetJdk.VERSION_1_7
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(PMD_EXTENSION_DEPRECATION)
        succeeds("help")
    }

    def "setting Pmd.targetJdk on a task emits a deprecation warning"() {
        given:
        buildFile << """
            tasks.named('pmdMain') {
                targetJdk = TargetJdk.VERSION_1_7
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(PMD_TASK_DEPRECATION)
        succeeds("pmdMain", "--dry-run")
    }

    def "setting PmdExtension.targetJdk via Object overload emits a deprecation warning"() {
        given:
        buildFile << """
            pmd {
                targetJdk = '1.7'
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(PMD_EXTENSION_OBJECT_DEPRECATION)
        succeeds("help")
    }

    def "calling TargetJdk.toVersion directly emits a deprecation warning"() {
        given:
        buildFile << """
            TargetJdk.toVersion('1.7')
        """

        expect:
        executer.expectDocumentedDeprecationWarning(TARGET_JDK_TYPE_DEPRECATION)
        succeeds("help")
    }
}
