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
 * The purpose of versions on {@link JavaToolchainSpec} is to allow
 * for the extension of the field set contained by them, i.e. to
 * enable more and more detailed ways of specifying toolchains.
 * <p>
 * The version of a {@code JavaToolchainSpec} is as high as the
 * newest type of toolchain specification criteria it employs, so
 * it's less about code versions being used and more about the
 * novelty of the actual ways of describing toolchains.
 *
 * @since 7.6
 */
@Incubating
public class JavaToolchainSpecVersion {

    public static int CURRENT_SPEC_VERSION = 1; //as of Gradle 7.6

}
