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

package org.gradle.integtests.fixtures.executer;

/**
 * Intended to be used with {@link org.gradle.integtests.tooling.fixture.TargetGradleVersion} and other similar things.
 */
public class GradleVersions {

    private GradleVersions() {
    }

    public static final String SUPPORTS_CONTINUOUS = ">=2.5";
    // We moved the API back into internal, so this isn't really correct.
    public static final String SUPPORTS_DEPLOYMENT_REGISTRY = ">=4.2";
}
