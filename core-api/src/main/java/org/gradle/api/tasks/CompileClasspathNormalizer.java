/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks;

/**
 * Normalizes file input that represents a Java compile classpath.
 *
 * Compared to the default behavior this normalizer keeps the order of any root files,
 * but ignores the order and timestamps of files in directories and ZIP/JAR files.
 * Compared to {@link ClasspathNormalizer} this normalizer only snapshots the ABIs of class files,
 * and ignores any non-class resource.
 *
 * @see org.gradle.api.tasks.CompileClasspath
 *
 * @since 4.3
 */
public interface CompileClasspathNormalizer extends FileNormalizer {
}
