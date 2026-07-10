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

package org.gradle.integtests.tooling.fixture

import com.google.common.annotations.VisibleForTesting
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.CommitDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.file.locking.ExclusiveFileAccessManager
import org.gradle.test.fixtures.file.TestFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads Tooling API clients of a given version, for use in cross version testing.
 */
class ToolingApiDistributionResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolingApiDistributionResolver.class)

    private final Map<String, ToolingApiDistribution> distributions = [:]
    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private final ExclusiveFileAccessManager fileAccessManager = new ExclusiveFileAccessManager(120000, 200)

    private final String repoUrl

    ToolingApiDistributionResolver() {
        this(RepoScriptBlockUtil.gradleRepositoryMirrorUrl())
    }

    @VisibleForTesting
    ToolingApiDistributionResolver(String repoUrl) {
        this.repoUrl = repoUrl
    }

    ToolingApiDistribution resolve(String toolingApiVersion) {
        if (!distributions[toolingApiVersion]) {
            if (useToolingApiFromTestClasspath(toolingApiVersion)) {
                distributions[toolingApiVersion] = new TestClasspathToolingApiDistribution()
            } else if (CommitDistribution.isCommitDistribution(toolingApiVersion)) {
                throw new UnsupportedOperationException(String.format("Commit distributions are not supported in this context. Adjust %s code to support them", this.class.canonicalName))
            } else {
                File toolingApiJar = locateToolingApi(toolingApiVersion)
                File slf4jApi = locateLocalSlf4j()
                distributions[toolingApiVersion] = new ExternalToolingApiDistribution(toolingApiVersion, [slf4jApi, toolingApiJar])
            }
        }
        distributions[toolingApiVersion]
    }

    private File locateToolingApi(String version) {
        def relativePath = "org/gradle/gradle-tooling-api/$version/gradle-tooling-api-${version}.jar"

        File localRepository = buildContext.localRepository
        if (localRepository) {
            Path jarFile = localRepository.toPath().resolve(relativePath)
            if (Files.exists(jarFile)) {
                return jarFile.toFile()
            }
        }

        TestFile destination = buildContext.tmpDir.file("gradle-tooling-api-${version}.jar")
        if (!destination.exists()) {
            def url = repoUrl + "/" + relativePath
            download(url, destination)
        }
        return destination
    }

    /**
     * The tooling API depends on the SLF4j API jar -- it is not packaged in the fat jar.
     * Just use the SLF4J version on the classpath instead of resolving it from a repo.
     */
    private static File locateLocalSlf4j() {
        File location = ClasspathUtil.getClasspathForClass(Logger.class)
        assert location.name.endsWith(".jar") : "Expected to find SLF4J jar"
        location
    }

    private void download(String url, TestFile destination) {
        def markerFile = destination.withExtension("ok")
        fileAccessManager.access(destination) {
            if (!markerFile.exists()) {
                destination.delete()

                LOGGER.warn("Downloading {}", url)
                destination.copyFrom(new URI(url).toURL())

                markerFile.createFile()
            }
        }
    }

    private boolean useToolingApiFromTestClasspath(String toolingApiVersion) {
        toolingApiVersion == buildContext.version.baseVersion.version
    }
}
