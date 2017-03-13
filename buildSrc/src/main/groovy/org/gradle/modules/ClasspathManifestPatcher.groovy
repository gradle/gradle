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
package org.gradle.modules

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.util.CollectionUtils
import org.gradle.util.GUtil

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

/**
 * Patches the classpath manifests of external modules such as gradle-script-kotlin
 * to match the dependencies of the Gradle runtime configuration.
 */
@CompileStatic
class ClasspathManifestPatcher {

    /**
     * The project.
     */
    ProjectInternal project

    /**
     * The Gradle runtime configuration.
     */
    Configuration runtime

    /**
     * The configuration containing the external modules whose classpath manifests must be patched.
     */
    Set<String> moduleNames

    File temporaryDir

    ClasspathManifestPatcher(ProjectInternal project, File temporaryDir, Configuration runtime, Set<String> moduleNames) {
        this.project = project
        this.runtime = runtime
        this.temporaryDir = temporaryDir
        this.moduleNames = moduleNames
    }

    def writePatchedFilesTo(File outputDir) {
        resolveExternalModuleJars().each { module ->
            def originalFile = mainArtifactFileOf(module)
            def patchedFile = new File(outputDir, originalFile.name)
            def unpackDir = unpack(originalFile)
            patchManifestOf(module, unpackDir)
            patchClassFilesIn(unpackDir)
            pack(unpackDir, patchedFile)
        }
    }

    /**
     * Resolves each external module against the runtime configuration.
     */
    private Collection<ResolvedDependency> resolveExternalModuleJars() {
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

    private static void patchClassFilesIn(File dir) {
        dir.eachFileRecurse {
            if (it.name.endsWith(".class")) {
                remapTypesOf(it, REMAPPINGS)
            }
        }
    }

    // Map of internal APIs that have been renamed since the last release
    private static final Map<String, String> REMAPPINGS = [
        "org/gradle/plugin/use/internal/PluginRequests": "org/gradle/plugin/management/internal/PluginRequests"
    ]

    private static void remapTypesOf(File classFile, Map<String, String> remappings) {
        ClassWriter classWriter = new ClassWriter(0)
        new ClassReader(classFile.bytes).accept(
            new ClassRemapper(classWriter, remapperFor(remappings)),
            ClassReader.EXPAND_FRAMES)
        classFile.bytes = classWriter.toByteArray()
    }

    private static Remapper remapperFor(Map<String, String> typeNameRemappings) {
        new Remapper() {
            @Override
            String map(String typeName) {
                typeNameRemappings[typeName] ?: typeName
            }
        }
    }

    private static String runtimeManifestOf(ResolvedDependency module) {
        def dependencies = module.allModuleArtifacts - module.moduleArtifacts
        dependencies.collect { it.file.name }.sort().join(',')
    }

    private File unpack(File file) {
        def unpackDir = new File(temporaryDir, file.name)
        project.sync { CopySpec spec ->
            spec.into(unpackDir)
            spec.from(project.zipTree(file))
        }
        unpackDir
    }

    @CompileDynamic
    private void pack(File baseDir, File destFile) {
        project.ant.zip(basedir: baseDir, destfile: destFile)
    }

    private static File mainArtifactFileOf(ResolvedDependency module) {
        CollectionUtils.single(module.moduleArtifacts).file
    }
}
