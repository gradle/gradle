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

import groovy.transform.CompileStatic

// TODO: rename the class. Not doing it as part of Spock2 upgrade change so that there is less non-essential changes noise
@CompileStatic
class GradleMetadataResolveRunner  {
    public final static String GRADLE_METADATA = "org.gradle.internal.resolution.testWithGradleMetadata"
    public final static String REPOSITORY_TYPE = "org.gradle.internal.resolution.testRepositoryType"

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
