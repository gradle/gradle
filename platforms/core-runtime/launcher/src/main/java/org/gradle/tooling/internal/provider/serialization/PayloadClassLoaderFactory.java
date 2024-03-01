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

package org.gradle.tooling.internal.provider.serialization;

import org.gradle.internal.classloader.ClassLoaderSpec;

import java.util.List;

/**
 * Used to create ClassLoaders used to serialize objects between the tooling api provider and daemon.
 *
 * <p>Implementations are not required to be thread-safe.</p>
 */
public interface PayloadClassLoaderFactory {
    ClassLoader getClassLoaderFor(ClassLoaderSpec spec, List<? extends ClassLoader> parents);
}
