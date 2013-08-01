/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import java.util.List;

/**
 * Details of a resolved module. Some details can be changed
 * via metadata rules (see {@link org.gradle.api.artifacts.dsl.ModuleHandler}.
 */
@Incubating
public interface ModuleDetails {
    /**
     * Returns the group of the module.
     *
     * @return the group of the module.
     */
    String getGroup();

    /**
     * Returns the name of the module.
     *
     * @return the name of the module
     */
    String getName();

    /**
     * Returns the version of the module.
     *
     * @return the version of the module
     */
    String getVersion();
    boolean isChanging();
    String getStatus();
    List<String> getStatusScheme();

    void setChanging(boolean changing);
    void setStatus(String status);
    void setStatusScheme(List<String> statusScheme);
}
