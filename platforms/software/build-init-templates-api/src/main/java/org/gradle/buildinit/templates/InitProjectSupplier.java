/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.buildinit.templates;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * Supplies project templates.
 *
 * @since 8.11
 */
@Incubating
public interface InitProjectSupplier {
    /**
     * Returns the project templates this supplier provides.
     *
     * @return the project templates
     * @since 8.11
     */
    List<InitProjectSpec> getProjectDefinitions();

    /**
     * Returns the project generator for the project types provided by this supplier.
     *
     * @return project generator
     * @since 8.11
     */
    InitProjectGenerator getProjectGenerator();
}
