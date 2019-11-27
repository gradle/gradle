/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.artifacts.verification;

import org.gradle.api.Incubating;

/**
 * The different dependency verification modes. By default, Gradle
 * will use the strict mode, which means that it will verify dependencies
 * and fail <i>as soon as possible</i>, to avoid as much compromising of
 * the builds as possible.
 *
 * There are, however, two additional modes which can be used: the lenient
 * one will collect all errors but only log them to the CLI. This is useful
 * when updating the file and you want to collect as many errors as possible
 * before failing.
 *
 * The last one is "off", meaning that even if verification metadata is
 * present, Gradle will not perform any verification. This can typically
 * be used whenever verification should only happen on CI.
 *
 * @since 6.2
 */
@Incubating
public enum DependencyVerificationMode {
    STRICT, // the default, fail as soon as possible
    LENIENT, // do not fail, but report all verification failures on console
    OFF // verification is disabled
}
