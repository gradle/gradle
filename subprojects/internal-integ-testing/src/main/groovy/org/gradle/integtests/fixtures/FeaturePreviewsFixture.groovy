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

package org.gradle.integtests.fixtures

class FeaturePreviewsFixture {

    static void enableGroovyCompilationAvoidance(File settings) {
        settings << """
enableFeaturePreview('GROOVY_COMPILATION_AVOIDANCE')
"""
    }

    static void enableOneLockfilePerProject(File settings) {
        settings << """
enableFeaturePreview('ONE_LOCKFILE_PER_PROJECT')
"""
    }

    static void enableUpdatedVersionSorting(File settings) {
        settings << """
            enableFeaturePreview('VERSION_ORDERING_V2')
"""
    }

    static void enableTypeSafeProjectAccessors(File settings) {
        settings << """
            enableFeaturePreview('TYPESAFE_PROJECT_ACCESSORS')
"""
    }
}
