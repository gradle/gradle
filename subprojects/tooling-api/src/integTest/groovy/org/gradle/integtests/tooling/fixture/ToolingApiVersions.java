/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.fixture;

/**
 * Values are intended to be used with {@link ToolingApiVersion}.
 */
public class ToolingApiVersions {

    private ToolingApiVersions() {
    }

    public static final String SUPPORTS_CANCELLATION = ">=2.1";
    public static final String SUPPORTS_RICH_PROGRESS_EVENTS = ">=2.5";
    public static final String PRE_CANCELLATION = ">=1.2 <2.1";

}
