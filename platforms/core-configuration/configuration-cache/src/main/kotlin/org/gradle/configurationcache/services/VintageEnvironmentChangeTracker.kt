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

package org.gradle.configurationcache.services

import org.gradle.initialization.EnvironmentChangeTracker


internal
class VintageEnvironmentChangeTracker : EnvironmentChangeTracker {

    override fun systemPropertyChanged(key: Any, value: Any?, consumer: String?) {}

    override fun systemPropertyLoaded(key: Any, value: Any?, oldValue: Any?) {}

    override fun systemPropertyOverridden(key: Any) {}
}
