/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform;

/**
 * A factory for creating {@link TargetMachine} objects.
 *
 * @since 5.1
 */
public interface TargetMachineFactory {
    /**
     * Returns a {@link TargetMachineBuilder} for the Windows operating system family and the architecture of the current host.
     */
    TargetMachineBuilder getWindows();

    /**
     * Returns a {@link TargetMachineBuilder} for the Linux operating system family and the architecture of the current host.
     */
    TargetMachineBuilder getLinux();

    /**
     * Returns a {@link TargetMachineBuilder} for the macOS operating system family and the architecture of the current host.
     */
    TargetMachineBuilder getMacOS();

    /**
     * Returns a {@link TargetMachineBuilder} representing the specified operating system and the architecture of the current host.
     */
    TargetMachineBuilder os(String operatingSystemFamily);
}
