/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import static org.gradle.cache.internal.WrapperDistributionCleanupAction.WRAPPER_DISTRIBUTION_FILE_PATH

trait VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture implements VersionSpecificCacheCleanupFixture {

    @Override
    TestFile getCachesDir() {
        gradleUserHomeDir.file(DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME)
    }

    TestFile createDistributionDir(GradleVersion version, String distributionType) {
        def distsDir = gradleUserHomeDir.createDir(WRAPPER_DISTRIBUTION_FILE_PATH)
        def versionDir = distsDir.file("gradle-${version.version}-$distributionType").createDir()
        return versionDir
    }

    abstract TestFile getGradleUserHomeDir()

}
