/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.model.eclipse;

/**
 * A tooling model that instructs Gradle to run tasks from the Eclipse plugin configuration.
 *
 * This is a special tooling model as it does not provide any information. However, when requested, Gradle will
 * override the client-provided tasks with the ones stored in the {@code eclipse.synchronizationTasks} property.
 *
 * This allows Buildship to run tasks before the model loading and load the models in a single step.
 *
 * @since 5.4
 */
public interface RunEclipseSynchronizationTasks {
}
