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

package org.gradle.cache.internal;

// Prebuilt, dependency-free class loaded in an isolated URLClassLoader by
// DefaultCrossBuildInMemoryCacheFactoryTest to prove a class cache lets a key Class (and its
// ClassLoader) be garbage-collected. Kept as pure Java so loading it never registers a ClassInfo
// in Groovy's global ClassInfo.globalClassSet, which would otherwise pin the loader.
public class Throwaway {}
