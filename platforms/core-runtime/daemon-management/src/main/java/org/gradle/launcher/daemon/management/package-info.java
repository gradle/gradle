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

/**
 * Version independent discovery and management of Gradle daemons.
 *
 * <p>Everything in this package is expected to work against daemons of Gradle versions other than
 * the one it ships with, including versions released after it. Types here therefore describe
 * daemons in terms that do not change between versions.
 */
@NullMarked
package org.gradle.launcher.daemon.management;

import org.jspecify.annotations.NullMarked;
