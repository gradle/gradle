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
package org.gradle.api.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ComponentMetaDataDetails;

/**
 * Configuration options related to modules resolved from a repository.
 */
@Incubating
public interface ModuleHandler {
    /**
     * Adds a rule to post-process the metadata of each resolved module.
     * This allows to, for example, set the module's status and status scheme
     * from within the build script, overriding any value specified in the
     * module's descriptor.
     *
     * @param action the rule to be added
     */
    void eachModule(Action<? super ComponentMetaDataDetails> action);
}
