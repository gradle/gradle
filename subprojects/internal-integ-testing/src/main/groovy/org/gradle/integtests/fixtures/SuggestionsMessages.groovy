/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.fixtures

/**
 * A collection of suggestions to be displayed to the user when a build fails.
 * These where repeated all over the test code, so they are now centralized here.
 */

class SuggestionsMessages {

    public static final String INFO_DEBUG = "Run with --info or --debug option to get more log output."
    public static final String DEBUG = "Run with --debug option to get more log output."
    public static final String SCAN = "Run with --scan to get full insights."
    public static final String GET_HELP = "Get more help at https://help.gradle.org"
    public static final String STACKTRACE_MESSAGE = "Run with --stacktrace option to get the stack trace."

    static String repositoryHint(String type){
        "If the artifact you are trying to retrieve can be found in the repository but without metadata in '$type' format, you need to adjust the 'metadataSources { ... }' of the repository declaration."
    }

}
