/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.extensions

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

/**
 * Runs tests with and without Gradle metadata support enabled
 */
@CompileStatic
class GradleMetadataResolveInterceptor extends BehindFlagFeatureInterceptor {
    public final static String GRADLE_METADATA = "org.gradle.internal.resolution.testWithGradleMetadata"
    public final static String REPOSITORY_TYPE = "org.gradle.internal.resolution.testRepositoryType"

    GradleMetadataResolveInterceptor(Class<?> target) {
        super(target, [
                (GRADLE_METADATA): booleanFeature("Gradle metadata"),
                (REPOSITORY_TYPE): new Feature(ivy: 'Ivy repository', maven: 'Maven repository')
            ],
            doNotExecuteAllPermutationsForNoDaemonExecuter())
    }

    private static boolean doNotExecuteAllPermutationsForNoDaemonExecuter() {
        !GradleContextualExecuter.isNoDaemon()
    }

    def isInvalidCombination(Map<String, String> values) {
        false
    }

    static boolean isGradleMetadataPublished() {
        System.getProperty(GRADLE_METADATA) == "true"
    }

    static boolean useIvy() {
        System.getProperty(REPOSITORY_TYPE) == "ivy"
    }

    static boolean useMaven() {
        System.getProperty(REPOSITORY_TYPE) == "maven"
    }
}
