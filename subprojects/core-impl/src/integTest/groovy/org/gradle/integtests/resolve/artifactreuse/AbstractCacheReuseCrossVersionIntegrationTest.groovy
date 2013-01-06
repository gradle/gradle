/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.integtests.resolve.artifactreuse

import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleBuiltDistribution

/**
 * by Szczepan Faber, created at: 11/27/12
 */
abstract class AbstractCacheReuseCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {

    /**
     * **** README ****
     *
     * If this test fails:
     *  1. Make sure BasicGradleDistribution.artifactCacheVersion settings are correct
     *  2. Think about improving this test so that we don't have to manually fix things ;)
     */

    void setup() {
        assert DefaultCacheLockingManager.CACHE_LAYOUT_VERSION == new GradleBuiltDistribution().artifactCacheLayoutVersion
    }
}
