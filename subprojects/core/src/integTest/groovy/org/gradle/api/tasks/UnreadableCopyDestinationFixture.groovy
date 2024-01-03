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

package org.gradle.api.tasks

import groovy.transform.SelfType
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

@SelfType(AbstractIntegrationSpec)
trait UnreadableCopyDestinationFixture {
    private static final String COPY_UNREADABLE_DESTINATION_FAILURE = "Cannot access a file in the destination directory. " +
        "Copying to a directory which contains unreadable content is not supported. " +
        "Declare the task as untracked by using Task.doNotTrackState(). " +
        new DocumentationRegistry().getDocumentationRecommendationFor("information", "incremental_build", "sec:disable-state-tracking")

    void expectUnreadableCopyDestinationFailure() {
        failure.assertHasDocumentedCause(COPY_UNREADABLE_DESTINATION_FAILURE)
    }
}
