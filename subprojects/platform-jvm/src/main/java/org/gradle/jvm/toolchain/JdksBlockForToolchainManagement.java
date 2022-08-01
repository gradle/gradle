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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.toolchain.management.JavaToolchainRepositoryRegistration;

/**
 * //TODO (#21082): docs
 *
 * @since 7.6
 */
@Incubating
public interface JdksBlockForToolchainManagement extends ExtensionAware {

    //TODO (#21082): name is probably not ok
    // TODO (#21082): should it be ExtensionAware or only its implementation do that?

    void request(String... registrationNames); //TODO (#21082): not how it should work in the final version, it should take a configuration block

    void request(JavaToolchainRepositoryRegistration... registrations); //TODO (#21082): not how it should work in the final version, it should take a configuration block

}
