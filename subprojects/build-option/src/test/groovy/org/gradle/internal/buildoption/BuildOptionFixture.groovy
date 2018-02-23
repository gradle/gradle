/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.buildoption

import org.gradle.cli.CommandLineOption

final class BuildOptionFixture {

    public static final String GRADLE_PROPERTY = 'org.gradle.test'
    public static final String LONG_OPTION = 'test'
    public static final String SHORT_OPTION = 't'
    public static final String DESCRIPTION = 'some test'

    private BuildOptionFixture() {}

    static void assertNoArguments(CommandLineOption option) {
        assert !option.allowsArguments
        assert !option.allowsMultipleArguments
    }

    static void assertSingleArgument(CommandLineOption option) {
        assert option.allowsArguments
        assert !option.allowsMultipleArguments
    }

    static void assertMultipleArgument(CommandLineOption option) {
        assert option.allowsArguments
        assert option.allowsMultipleArguments
    }

    static void assertIncubating(CommandLineOption option, boolean incubating) {
        assert option.incubating == incubating
    }

    static void assertDeprecated(CommandLineOption option) {
        assert option.deprecated
    }

    static void assertNotDeprecated(CommandLineOption option) {
        assert !option.deprecated
    }

    static void assertDescription(CommandLineOption option) {
        assertDescription(option, DESCRIPTION)
    }

    static void assertDescription(CommandLineOption option, String desc) {
        assert option.description == desc
    }

    static void assertIncubatingDescription(CommandLineOption option, boolean incubating) {
        assertIncubatingDescription(option, incubating, DESCRIPTION)
    }

    static void assertIncubatingDescription(CommandLineOption option, boolean incubating, String desc) {
        assert option.description == desc + (incubating ? ' [incubating]' : '')
    }

    static void assertDeprecatedDescription(CommandLineOption option, boolean deprecated) {
        assertDeprecatedDescription(option, deprecated, DESCRIPTION)
    }

    static void assertDeprecatedDescription(CommandLineOption option, boolean deprecated, String desc) {
        assert option.description == desc + (deprecated ? ' [deprecated]' : '')
    }
}
