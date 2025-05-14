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

package org.gradle.internal.cc.impl

import org.gradle.internal.cc.impl.initialization.ConfigurationCacheIncompatibleUserCodeContext
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.invocation.ConfigurationCacheDegradationController

class DefaultConfigurationCacheDegradationController(
    private val incompatibleUserCodeContext: ConfigurationCacheIncompatibleUserCodeContext,
    private val userCodeApplicationContext: UserCodeApplicationContext
) : ConfigurationCacheDegradationController {

    override fun notCompatibleWithConfigurationCache(reason: String, action: Runnable) {
        val userCodeSource = userCodeApplicationContext.current()?.source
        userCodeSource?.let { source ->
            try {
                incompatibleUserCodeContext.enterUserCode(source, reason)
                action.run()
            } finally {
                incompatibleUserCodeContext.leaveUserCode(source)
            }
        }
    }
}
