/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class DefaultCacheScopeMappingTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def userHome = tmpDir.createDir("user-home")
    def gradleVersion = Stub(GradleVersion) {
        getVersion() >> "version"
    }
    def mapping = new DefaultCacheScopeMapping(userHome, null, gradleVersion)

    def "null scope maps to user home directory"() {
        expect:
        mapping.getBaseDirectory(null, "key", VersionStrategy.CachePerVersion) == userHome.file("caches/version/key")
        mapping.getBaseDirectory(null, "key", VersionStrategy.SharedCache) == userHome.file("caches/key")
    }

    def "Gradle scope maps to root project directory"() {
        def rootDir = tmpDir.createDir("root")
        def rootProject = Stub(Project) {
            getProjectDir() >> rootDir
        }
        def gradle = Stub(Gradle) {
            getRootProject() >> rootProject
        }

        expect:
        mapping.getBaseDirectory(gradle, "key", VersionStrategy.CachePerVersion) == rootDir.file(".gradle/version/key")
        mapping.getBaseDirectory(gradle, "key", VersionStrategy.SharedCache) == rootDir.file(".gradle/key")
    }

    def "Project scope maps to child of root project directory"() {
        def rootDir = tmpDir.createDir("root")
        def rootProject = Stub(Project) {
            getProjectDir() >> rootDir
            getPath() >> ":"
        }
        rootProject.rootProject >> rootProject
        def childProject = Stub(Project) {
            getRootProject() >> rootProject
            getPath() >> ":child1:child2"
        }

        expect:
        mapping.getBaseDirectory(rootProject, "key", VersionStrategy.CachePerVersion) == rootDir.file(".gradle/version/projects/_/key")
        mapping.getBaseDirectory(rootProject, "key", VersionStrategy.SharedCache) == rootDir.file(".gradle/projects/_/key")
        mapping.getBaseDirectory(childProject, "key", VersionStrategy.CachePerVersion) == rootDir.file(".gradle/version/projects/_child1_child2/key")
    }

    def "Task scope maps to child of root project directory"() {
        def rootDir = tmpDir.createDir("root")
        def rootProject = Stub(Project) {
            getProjectDir() >> rootDir
        }
        def childProject = Stub(Project) {
            getRootProject() >> rootProject
        }
        def task = Stub(Task) {
            getProject() >> childProject
            getPath() >> ":project:task"
        }

        expect:
        mapping.getBaseDirectory(task, "key", VersionStrategy.CachePerVersion) == rootDir.file(".gradle/version/tasks/_project_task/key")
        mapping.getBaseDirectory(task, "key", VersionStrategy.SharedCache) == rootDir.file(".gradle/tasks/_project_task/key")
    }

    def "Can override the build specific cache directory"() {
        def customCacheDir = tmpDir.createDir("other")
        def rootDir = tmpDir.createDir("root")
        def rootProject = Stub(Project) {
            getProjectDir() >> rootDir
        }
        def gradle = Stub(Gradle) {
            getRootProject() >> rootProject
        }
        def childProject = Stub(Project) {
            getRootProject() >> rootProject
            getPath() >> ":child1:child2"
        }
        def task = Stub(Task) {
            getProject() >> childProject
            getPath() >> ":project:task"
        }
        def mapping = new DefaultCacheScopeMapping(userHome, customCacheDir, gradleVersion)

        expect:
        mapping.getBaseDirectory(null, "key", VersionStrategy.CachePerVersion) == userHome.file("caches/version/key")
        mapping.getBaseDirectory(gradle, "key", VersionStrategy.CachePerVersion) == customCacheDir.file("version/key")
        mapping.getBaseDirectory(childProject, "key", VersionStrategy.SharedCache) == customCacheDir.file("projects/_child1_child2/key")
        mapping.getBaseDirectory(task, "key", VersionStrategy.CachePerVersion) == customCacheDir.file("version/tasks/_project_task/key")
    }

    @Unroll
    def "can't use badly-formed key '#key'"() {
        when:
        mapping.getBaseDirectory(null, key, VersionStrategy.CachePerVersion)

        then:
        thrown(IllegalArgumentException)

        where:
        key << ["tasks", "projects", "1.11", "1.2.3.4", "", "/", "..", "c:/some-dir", "\n", "a\\b", " no white space "]
    }

    @Unroll
    def "can use well-formed key '#key'"() {
        when:
        mapping.getBaseDirectory(null, key, VersionStrategy.CachePerVersion)

        then:
        noExceptionThrown()

        where:
        key << ["abc", "a/b/c", "module-1.2"]
    }

    def "can locate cache root dir when custom project-cache-dir is used "() {
        def projectCacheDir = tmpDir.createDir("other")
        def rootDir = tmpDir.createDir("root")
        def rootProject = Stub(Project) {
            getProjectDir() >> rootDir
        }
        def gradle = Stub(Gradle) {
            getRootProject() >> rootProject
        }
        def childProject = Stub(Project) {
            getRootProject() >> rootProject
        }
        def task = Stub(Task) {
            getProject() >> childProject
        }
        def mapping = new DefaultCacheScopeMapping(userHome, projectCacheDir, gradleVersion)

        expect:
        mapping.getRootDirectory(null) == userHome.file("caches")
        mapping.getRootDirectory(gradle) == projectCacheDir
        mapping.getRootDirectory(childProject) == projectCacheDir
        mapping.getRootDirectory(task) == projectCacheDir
    }

    def "can locate cache root dir when default project-cache-dir is used "() {
        def rootDir = tmpDir.createDir("root")
        def defaultCacheDir = rootDir.createDir(".gradle")
        def rootProject = Stub(Project) {
            getProjectDir() >> rootDir
        }
        def gradle = Stub(Gradle) {
            getRootProject() >> rootProject
        }
        def childProject = Stub(Project) {
            getRootProject() >> rootProject
        }
        def task = Stub(Task) {
            getProject() >> childProject
        }
        def mapping = new DefaultCacheScopeMapping(userHome, null, gradleVersion)

        expect:
        mapping.getRootDirectory(null) == userHome.file("caches")
        mapping.getRootDirectory(gradle) == defaultCacheDir
        mapping.getRootDirectory(childProject) == defaultCacheDir
        mapping.getRootDirectory(task) == defaultCacheDir
    }
}
