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

package org.gradle.plugins.ide.eclipse.internal;

public class EclipsePluginConstants {

    public static final String DEFAULT_PROJECT_OUTPUT_PATH = "bin/default";
    public static final String TEST_SOURCES_ATTRIBUTE_KEY = "test";
    public static final String TEST_SOURCES_ATTRIBUTE_VALUE = "true";
    public static final String MODULE_ATTRIBUTE_KEY = "module";
    public static final String MODULE_ATTRIBUTE_VALUE = "true";

    // TODO The scope information is superseded by test attributes. We can delete the corresponding code bits once we make sure that the majority of Buildship users use test sources.
    public static final String GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME = "gradle_used_by_scope";
    public static final String GRADLE_SCOPE_ATTRIBUTE_NAME = "gradle_scope";

    public static final String WITHOUT_TEST_CODE_ATTRIBUTE_KEY = "without_test_code";
    public static final String WITHOUT_TEST_CODE_ATTRIBUTE_VALUE = "true";

    private EclipsePluginConstants() {
    }

}
