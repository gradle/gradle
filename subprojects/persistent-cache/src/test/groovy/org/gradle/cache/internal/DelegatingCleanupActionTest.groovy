/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.cache.CleanableStore
import org.gradle.cache.CleanupAction
import org.gradle.cache.CleanupProgressMonitor
import spock.lang.Specification
import spock.lang.Subject

class DelegatingCleanupActionTest extends Specification {

    def delegateAction = Mock(CleanupAction)
    def fileAccessTimeProvider = Mock(CacheCleanupFileAccessTimeProvider)

    def cleanableStore = Stub(CleanableStore)
    def progressMonitor = Stub(CleanupProgressMonitor)

    @Subject cleanupAction = new DelegatingCleanupAction(delegateAction, fileAccessTimeProvider)

    def "delegates cleanup and closes provider"() {
        when:
        cleanupAction.clean(cleanableStore, progressMonitor)

        then:
        1 * delegateAction.clean(cleanableStore, progressMonitor)

        then:
        1 * fileAccessTimeProvider.close()
    }

}
