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

package org.gradle.api;

/**
 * Compile-time stub for {@code org.gradle.api.Buildable} from {@code :core-api}.
 *
 * <p>Lets {@code :provider-api} compile types whose hierarchy reaches into the Gradle model
 * without depending on it. The stubs are compileOnly and never part of the distribution;
 * at runtime the real classes from {@code :core-api} are used.
 *
 * <p>Must stay member-free so no code can compile against members the real type may not have.
 */
public interface Buildable {
}
