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

package org.gradle.initialization.buildsrc;

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;

/**
 * Can be implemented by plugins to auto-configure the buildSrc root project.
 *
 * <p>Implementations are discovered using the JAR service locator mechanism (see {@link org.gradle.internal.service.ServiceLocator}).
 * Each action is invoked for the buildSrc project that is to be configured, before the project has been configured. Actions are executed
 * in an arbitrary order.
 */
public interface BuildSrcProjectConfigurationAction extends Action<ProjectInternal> {
}
