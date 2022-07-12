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

/**
 * TODO
 *
 * @since 7.6
 */
@Incubating
public interface JavaToolchainRepositoryRegistry {

    //TODO: how to access this registry from Settings? it's not part of the Core API, nor do we want to make it so (I guess)...

    /**
     * TODO
     *
     */
    <T extends JavaToolchainRepository> void register(String name, Class<T> implementationType);
    //TODO: do we also need a configure action, like we have in BuildServiceRegistry
    //TODO: does this method need to be a "registerIfAbsent" instead

}
