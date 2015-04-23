/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.filewatch.FileWatcher
import org.gradle.internal.filewatch.FileWatcherFactory
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class DefaultTriggerGeneratorFactoryTest extends Specification {
    def executorFactory = Mock(ExecutorFactory)
    def fileWatcherFactory = Mock(FileWatcherFactory)
    def triggerListener = Mock(TriggerListener)
    def triggerGeneratorFactory = new DefaultTriggerGeneratorFactory(executorFactory, fileWatcherFactory, triggerListener)

    def "creates trigger generator"() {
        given:
        fileWatcherFactory.createFileWatcher(_) >> Mock(FileWatcher)
        expect:
        triggerGeneratorFactory.newInstance() instanceof DefaultTriggerGenerator
    }
}
