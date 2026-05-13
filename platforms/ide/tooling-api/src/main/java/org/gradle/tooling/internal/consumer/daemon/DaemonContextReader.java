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
package org.gradle.tooling.internal.consumer.daemon;

import org.gradle.internal.serialize.Decoder;

/**
 * Reads the version-specific {@code DefaultDaemonContext} portion of a daemon registry
 * entry. The outer envelope (addresses, DaemonInfo wrapper, stop events) is stable across
 * supported Gradle versions; only the nested DaemonContext binary layout has shifted.
 */
interface DaemonContextReader {
    DaemonContextView read(Decoder decoder) throws Exception;
}
