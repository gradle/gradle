/*
 * Copyright 2026 the original author or authors.
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

/**
 * Thrown when the metadata of a candidate configuration cache entry cannot be read back, e.g. because it is corrupted.
 *
 * This marks a failure to read the entry itself, as opposed to a failure while evaluating the entry's fingerprint
 * (such as a value source throwing or an integrity check detecting a problem), which must be reported as-is.
 */
internal class ConfigurationCacheEntryReadException(cause: Throwable) : RuntimeException(cause)
