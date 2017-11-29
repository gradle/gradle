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
package org.gradle.integtests.fixtures.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ExperimentalFeaturesFixture
import org.gradle.test.fixtures.GradleMetadataAwarePublishingSpec
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenJavaModule

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

abstract class AbstractMavenPublishIntegTest extends AbstractIntegrationSpec implements GradleMetadataAwarePublishingSpec {

    def setup() {
        prepare()
    }

    protected static MavenJavaModule javaLibrary(MavenFileModule mavenFileModule) {
        return new MavenJavaModule(mavenFileModule)
    }

    protected def resolveArtifact(MavenModule module, def extension, def classifier) {
        resolveArtifacts("""
    dependencies {
        resolve group: '${sq(module.groupId)}', name: '${sq(module.artifactId)}', version: '${sq(module.version)}', classifier: '${sq(classifier)}', ext: '${sq(extension)}'
    }
""")
    }

    protected def resolveArtifacts(MavenModule module, boolean expectSameResultWithModuleMetadata = true, boolean expectFail = false) {
        resolveArtifacts("""
    dependencies {
        resolve group: '${sq(module.groupId)}', name: '${sq(module.artifactId)}', version: '${sq(module.version)}'
    }
""", expectSameResultWithModuleMetadata, expectFail)
    }

    protected def resolveArtifacts(MavenModule module, Map... additionalArtifacts) {
        def dependencies = """
    dependencies {
        resolve group: '${sq(module.groupId)}', name: '${sq(module.artifactId)}', version: '${sq(module.version)}'
        resolve(group: '${sq(module.groupId)}', name: '${sq(module.artifactId)}', version: '${sq(module.version)}') {
"""
        additionalArtifacts.each {
            // Docs say type defaults to 'jar', but seems it must be set explicitly
            def type = it.type == null ? 'jar' : it.type
            dependencies += """
            artifact {
                name = '${sq(module.artifactId)}'
                classifier = '${it.classifier}'
                type = '${type}'
            }
"""
        }
        dependencies += """
        }
    }
"""
        resolveArtifacts(dependencies)
    }

    protected def resolveArtifacts(String dependencies, boolean expectSameResultWithModuleMetadata = true, boolean expectFail = false) {
        def resolvedArtifacts = doResolveArtifacts(dependencies, false, null, expectFail)
        if (expectFail) {
            return resolvedArtifacts
        }

        if (resolveModuleMetadata) {
            def moduleArtifacts = doResolveArtifacts(dependencies, true)
            if (expectSameResultWithModuleMetadata) {
                assert resolvedArtifacts == moduleArtifacts
            } else {
                return moduleArtifacts
            }
        }

        return resolvedArtifacts
    }

    protected def resolveApiArtifacts(MavenModule module) {
        doResolveArtifacts("""
    dependencies {
        resolve group: '${sq(module.groupId)}', name: '${sq(module.artifactId)}', version: '${sq(module.version)}'
    }
""", true, "JAVA_API")
    }

    protected def resolveRuntimeArtifacts(MavenModule module) {
        doResolveArtifacts("""
    dependencies {
        resolve group: '${sq(module.groupId)}', name: '${sq(module.artifactId)}', version: '${sq(module.version)}'
    }
""", true, "JAVA_RUNTIME")
    }

    protected def doResolveArtifacts(def dependencies, def useGradleMetadata = false, def targetVariant = null, def expectFail = false) {
        // Replace the existing buildfile with one for resolving the published module
        settingsFile.text = "rootProject.name = 'resolve'"
        if (useGradleMetadata) {
            ExperimentalFeaturesFixture.enable(settingsFile)
        } else {
            executer.beforeExecute {
                // Remove the experimental flag set earlier...
                // TODO:DAZ Remove this once we support excludes and we can have a single flag to enable publish/resolve
                withArguments()
            }
        }
        def attributes = targetVariant == null ?
            "" :
            """ 
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.${targetVariant}))
    }
"""
        buildFile.text = """
            configurations {
                resolve {
                    ${attributes}
                }
            }
            repositories {
                maven { 
                    url "${mavenRepo.uri}"
                }
                ${mavenCentralRepositoryDefinition()}
            }
            $dependencies
            task resolveArtifacts(type: Sync) {
                outputs.upToDateWhen { false }
                from configurations.resolve
                into "artifacts"
            }

"""

        expectFail ? fails("resolveArtifacts") : run("resolveArtifacts")
        def artifactsList = file("artifacts").exists() ? file("artifacts").list() : []
        return artifactsList.sort()
    }


    String sq(String input) {
        return escapeForSingleQuoting(input)
    }

    String escapeForSingleQuoting(String input) {
        return input.replace('\\', '\\\\').replace('\'', '\\\'')
    }
}
