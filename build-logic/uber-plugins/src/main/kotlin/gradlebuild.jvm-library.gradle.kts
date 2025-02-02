/*
 * Copyright 2024 the original author or authors.
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

import com.autonomousapps.DependencyAnalysisSubExtension

plugins {
    id("gradlebuild.dependency-modules")
    id("gradlebuild.repositories")
    id("gradlebuild.minify")
    id("gradlebuild.reproducible-archives")
    id("gradlebuild.unittest-and-compile")
    id("gradlebuild.test-fixtures")
    id("gradlebuild.distribution-testing")
    id("gradlebuild.incubation-report")
    id("gradlebuild.strict-compile")
    id("gradlebuild.code-quality")
    id("gradlebuild.arch-test")
    id("gradlebuild.integration-tests")
    id("gradlebuild.cross-version-tests")
    id("gradlebuild.ci-lifecycle")
    id("gradlebuild.ci-reporting") // CI: Prepare reports to be uploaded to TeamCity
    id("gradlebuild.configure-ci-artifacts") // CI: Prepare reports to be uploaded to TeamCity
    id("com.autonomousapps.dependency-analysis") // Auditing dependencies to find unused libraries
}

configure<DependencyAnalysisSubExtension> {
    issues {
        onAny {
            severity("fail")
        }

        onUnusedAnnotationProcessors {
            // Ignore check for internal-instrumentation-processor, since we apply
            // it to all distribution.api-java projects but projects might not have any upgrade
            exclude(":internal-instrumentation-processor")
        }

        ignoreSourceSet("archTest", "crossVersionTest", "docsTest", "integTest", "jmh", "peformanceTest", "smokeTest", "testInterceptors", "testFixtures", "smokeIdeTest")
    }
}
