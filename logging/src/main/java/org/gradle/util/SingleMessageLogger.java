/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.util;


// Used by https://plugins.gradle.org/plugin/nebula.dependency-recommender 9.0.1
// https://github.com/nebula-plugins/nebula-project-plugin/commit/5f56397384328e24c506b0e2b395d1634dbf600f
/**
 * This class is only here to maintain binary compatibility with existing plugins.
 *
 * @deprecated Will be removed in Gradle 8.0.
 */
@Deprecated
public class SingleMessageLogger extends org.gradle.internal.deprecation.DeprecationLogger {
}
