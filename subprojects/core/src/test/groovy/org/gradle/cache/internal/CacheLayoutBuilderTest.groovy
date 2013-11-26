/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.Project
import org.gradle.cache.CacheLayout
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

class CacheLayoutBuilderTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    private TestFile globalCacheDir = tmpDir.createDir("global")
    private TestFile projectCacheDir = tmpDir.createDir("project")
    private final String version = GradleVersion.current().version
    private static final String KEY = "KEY"
    private final CacheLayoutBuilder builder = new CacheLayoutBuilder()

    public CacheLayout getLayout() {
        return builder.build()
    }

    public def getCacheDir() {
        return builder.build().getCacheDir(globalCacheDir, projectCacheDir, KEY)
    }

    public def getLayoutProperties() {
        return builder.build().applyLayoutProperties([:])
    }

    public void "builds shared global cache layout"() {
        when:
        builder.withSharedCache().withGlobalScope()

        then:
        cacheDir == globalCacheDir.file(KEY)
        layoutProperties == [:]
    }

    public void "builds versioned global cache layout"() {
        when:
        builder.withGlobalScope()

        then:
        cacheDir == globalCacheDir.file(version, KEY)
        layoutProperties == [:]
    }

    public void "builds version invalidating shared global cache layout"() {
        when:
        builder.withGlobalScope().withSharedCacheThatInvalidatesOnVersionChange()

        then:
        cacheDir == globalCacheDir.file("noVersion", KEY)
        layoutProperties == ["gradle.version" : version]
    }

    public void "uses supplied project directory for build scope"() {
        def rootProject = Mock(Project)
        def project = Stub(Project) {
            getRootProject() >> rootProject
        }

        when:
        builder.withBuildScope(project).withSharedCache()

        then:
        cacheDir == projectCacheDir.file(KEY)
        layoutProperties == [:]

        and:
        0 * rootProject._
    }

    public void "uses project root directory for build scope when supplied directory is null"() {
        final projectDir = tmpDir.createDir("other-project-dir")
        def rootProject = Stub(Project) {
            getProjectDir() >> projectDir
        }
        def project = Stub(Project) {
            getRootProject() >> rootProject
        }

        when:
        projectCacheDir = null
        builder.withBuildScope(project).withSharedCache()

        then:
        cacheDir == projectDir.file(".gradle", KEY)
        layoutProperties == [:]
    }

    public void "uses scope file for cache dir"() {
        final scopeDir = tmpDir.createDir("scope-dir")
        when:
        builder.withScope(scopeDir).withSharedCache()

        then:
        cacheDir == scopeDir.file(".gradle", KEY)
    }
}
