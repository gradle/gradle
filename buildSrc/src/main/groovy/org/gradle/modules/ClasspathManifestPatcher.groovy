
/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.util.GUtil;
import org.gradle.util.CollectionUtils;

/**
 * Patches the classpath manifests of external modules such as gradle-script-kotlin
 * to match the dependencies of the Gradle runtime configuration.
 */
class ClasspathManifestPatcher {

    /**
     * The project.
     */
    Project project

    /**
     * The Gradle runtime configuration.
     */
    Configuration runtime

    /**
     * The configuration containing the external modules whose classpath manifests must be patched.
     */
    Configuration external

    ClasspathManifestPatcher(Project project, Configuration runtime, Configuration external) {
        this.project = project
        this.runtime = runtime
        this.external = external
    }

    def writePatchedFilesTo(File outputDir) {
        resolveExternalModuleJars().each { module ->
            def originalFile = mainArtifactFileOf(module)
            def patchedFile = new File(outputDir, originalFile.name)
            def unpackDir = unpack(originalFile)
            patchManifestOf(module, unpackDir)
            pack(unpackDir, patchedFile)
        }
    }

    /**
     * Resolves each external module against the runtime configuration.
     */
    private Collection<ResolvedDependency> resolveExternalModuleJars() {
        def moduleNames = external.dependencies.collect { it.name }.toSet()
        runtime
            .resolvedConfiguration
            .firstLevelModuleDependencies
            .findAll { it.moduleName in moduleNames }
    }

    private static void patchManifestOf(ResolvedDependency module, File unpackDir) {
        def classpathManifestFile = new File(unpackDir, "${module.moduleName}-classpath.properties")
        def classpathManifest = GUtil.loadProperties(classpathManifestFile)
        classpathManifest.runtime = runtimeManifestOf(module)
        classpathManifestFile.text = classpathManifest.collect { "${it.key}=${it.value}" }.join('\n')
    }

    private static String runtimeManifestOf(ResolvedDependency module) {
        def dependencies = module.allModuleArtifacts - module.moduleArtifacts
        dependencies.collect { it.file.name }.sort().join(',')
    }

    private File unpack(File file) {
        def unpackDir = project.file("${project.buildDir}/external/unpack/${file.name}")
        project.copy { spec ->
            spec.into(unpackDir)
            spec.from(project.zipTree(file))
        }
        unpackDir
    }

    private void pack(File baseDir, File destFile) {
        project.ant.zip(basedir: baseDir, destfile: destFile)
    }

    private static File mainArtifactFileOf(ResolvedDependency module) {
        CollectionUtils.single(module.moduleArtifacts).file
    }
}
