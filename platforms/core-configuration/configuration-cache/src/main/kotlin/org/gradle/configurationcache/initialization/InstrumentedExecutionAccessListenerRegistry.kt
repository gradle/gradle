/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.initialization

import org.gradle.configurationcache.InstrumentedExecutionAccessListener
import org.gradle.internal.classpath.InstrumentedExecutionAccess
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.scopes.ListenerService


@ListenerService
internal
class InstrumentedExecutionAccessListenerRegistry(
    instrumentedExecutionAccessListener: InstrumentedExecutionAccessListener
) : Stoppable {

    init {
        InstrumentedExecutionAccess.setListener(instrumentedExecutionAccessListener)
    }

    override fun stop() {
        InstrumentedExecutionAccess.discardListener()
    }
}
