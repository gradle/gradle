/*
 * Copyright 2016 the original author or authors.
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
 * Classes for build caches.
 *
 * @since 3.3
 */

// We already have a similar package-info.java in :build-cache-spi
// We must have a package-info.java for every package directory, but for each package
// @NullMarked must only be declared once, otherwise `:docs:javadocAll` fails.
//@NullMarked
package org.gradle.caching;
