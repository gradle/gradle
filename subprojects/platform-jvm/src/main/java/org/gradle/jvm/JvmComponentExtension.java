/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * The configuration for jvm components created by this build.
 */
// TODO:DAZ Remove this. It's just a hack to allow Jar binaries to be configured in the DSL.
// Can't use binaries.all since the action needs to execute _after_ the plugin-supplied actions
// There's a story in the design doc to do this properly
@Incubating
public interface JvmComponentExtension {
    void allBinaries(Action<? super JvmBinarySpec> action);

    Action<JvmBinarySpec> getAllBinariesAction();
}
