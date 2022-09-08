/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.env;

import org.gradle.api.Incubating;

/**
 * Information about the environment the build is running in.
 *
 * @since 7.6
 */
@Incubating
public interface BuildEnvironment { //TODO (#21082): merge this into a unique param of the "toURI" method in the registry

    OperatingSystem getOperatingSystem();

    String getOperatingSystemName();

    Architecture getArchitecture();

    String getArchitectureName();

}
