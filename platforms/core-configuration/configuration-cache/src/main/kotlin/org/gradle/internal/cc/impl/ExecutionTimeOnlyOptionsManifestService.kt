/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.Task
import org.gradle.api.internal.tasks.TaskOptionsGenerator
import org.gradle.api.internal.tasks.options.OptionReader
import org.gradle.api.internal.tasks.options.OptionValidationException
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.ScheduledWork
import org.gradle.internal.cc.base.logger
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption


/**
 * The configuration cache's `execution-time-only options` facility, with two distinct
 * responsibilities:
 *
 * 1. **Manifest IO** ([taskOptionNames], [write]) — persists the set of CLI option
 *    names declared `@Option(executionTimeOnly = true)` by root-build tasks. The
 *    manifest at [manifestFile] is a UTF-8 newline-delimited file with a `#`-prefixed
 *    header (currently `#v1`) followed by sorted option names. It is rewritten in full
 *    at the end of every successful configuration.
 *
 * 2. **Pure CLI helpers** ([stripFrom], [extractFrom] in the companion) — partition
 *    an argument list around the candidate names. Used by [ConfigurationCacheKey] to
 *    omit execution-time-only values from the cache key, and by [ConfigurationCacheState]
 *    to re-apply them to tasks after a successful load.
 *
 * 3. **Contributor lookup** ([findExecutionTimeOnlyContributor] in the companion) —
 *    scans a work graph to identify which task declares a given option as execution-time-only.
 *    Used at validation-failure time to populate
 *    [ExecutionTimeOnlyOptionsValidationException]'s contributor field.
 *
 * A missing, unreadable, or unrecognized manifest yields an empty set: disabling
 * key-stripping is always safer than acting on bad input. Scope is the root build only;
 * included-build options are out of scope for v1.
 */
@ServiceScope(Scope.BuildTree::class)
internal class ExecutionTimeOnlyOptionsManifestService(
    private val cacheRepository: ConfigurationCacheRepository
) {
    /**
     * Absolute path of the manifest file. Exposed for diagnostics (error/warning logs);
     * IO callers should use [taskOptionNames] / [write].
     */
    val manifestFile: File
        get() = File(cacheRepository.cacheDir, FILE_NAME)

    /**
     * Returns the option names recorded in the manifest by the previous successful
     * configuration, sorted lexicographically. Performs a disk read on every call.
     *
     * An empty result means the manifest is absent, empty, or could not be parsed
     * (missing/unknown header, IO error). Callers must treat an empty result the same
     * as "no candidates" — never as a failure signal — so that corruption can never
     * produce wrong build results, only extra cache misses.
     */
    fun taskOptionNames(): Set<String> {
        val f = manifestFile
        if (!f.isFile) return emptySet()
        return try {
            f.useLines(StandardCharsets.UTF_8) { lines ->
                val nonEmpty = lines.map { it.trim() }.filter { it.isNotEmpty() }.iterator()
                if (!nonEmpty.hasNext()) return@useLines emptySet()
                val header = nonEmpty.next()
                if (!header.startsWith("#")) {
                    logger.warn("Ignoring execution-time-only options manifest at {}: missing header", f)
                    return@useLines emptySet()
                }
                if (header != CURRENT_HEADER) {
                    logger.info("Ignoring execution-time-only options manifest at {}: unsupported header {}", f, header)
                    return@useLines emptySet()
                }
                nonEmpty.asSequence()
                    .filter { !it.startsWith("#") }
                    .toSortedSet()
            }
        } catch (e: IOException) {
            logger.warn("Could not read execution-time-only options manifest at {}: {}", f, e.message)
            emptySet()
        }
    }

    /**
     * Atomically replaces the manifest with the given option names. Names are written
     * sorted; an empty set produces a header-only file.
     *
     * Throws `IOException` on failure. The caller is responsible for invalidating the
     * just-stored cache entry in that case, since downstream loads would otherwise
     * consult a stale manifest (see
     * [DefaultConfigurationCache.writeExecutionTimeOnlyOptionsManifest]).
     */
    fun write(names: Set<String>) {
        val f = manifestFile
        val parent = f.parentFile
        parent.mkdirs()
        val tempPath = Files.createTempFile(parent.toPath(), TEMP_PREFIX, TEMP_SUFFIX)
        var moved = false
        try {
            Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8).use { writer ->
                writer.write(CURRENT_HEADER)
                writer.newLine()
                for (name in names.toSortedSet()) {
                    writer.write(name)
                    writer.newLine()
                }
            }
            try {
                Files.move(tempPath, f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                // Cross-device / NFS / certain Windows configurations don't support ATOMIC_MOVE.
                // Falling back to a non-atomic replace is still safe-enough: taskOptionNames()
                // tolerates empty/missing/corrupt files by returning emptySet(), which
                // conservatively disables key-stripping rather than producing wrong results.
                Files.move(tempPath, f.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            moved = true
            // Match the 0600 permission policy enforced on per-entry state files.
            cacheRepository.applyEntryPermissions(f)
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(tempPath)
                } catch (_: IOException) {
                    // Best-effort cleanup. A leftover temp file is harmless: taskOptionNames()
                    // only looks at FILE_NAME, and the next successful write will use a fresh
                    // randomized temp name.
                }
            }
        }
    }

    companion object {

        private const val CURRENT_HEADER = "#v1"
        private const val FILE_NAME = "execution-time-only-options.manifest"
        private const val TEMP_PREFIX = "execution-time-only-options-"
        private const val TEMP_SUFFIX = ".tmp"

        /**
         * Returns [args] without any tokens belonging to [candidateNames].
         * Handles both `--name value` and `--name=value` shapes.
         */
        fun stripFrom(args: List<String>, candidateNames: Set<String>): List<String> {
            if (candidateNames.isEmpty()) return args
            val flagTokens = candidateNames.toFlagTokens()
            val result = ArrayList<String>(args.size)
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                when {
                    arg in flagTokens -> i += 2
                    arg.isKeyValueFor(flagTokens) -> i += 1
                    else -> {
                        result.add(arg)
                        i += 1
                    }
                }
            }
            return result
        }

        /**
         * Returns only the tokens in [args] that belong to [candidateNames].
         * Preserves both `--name value` pairs and `--name=value` shapes.
         */
        fun extractFrom(args: List<String>, candidateNames: Set<String>): List<String> {
            if (candidateNames.isEmpty()) return emptyList()
            val flagTokens = candidateNames.toFlagTokens()
            val result = ArrayList<String>()
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                when {
                    arg in flagTokens -> {
                        result.add(arg)
                        if (i + 1 < args.size) {
                            result.add(args[i + 1])
                            i += 2
                        } else {
                            i += 1
                        }
                    }
                    arg.isKeyValueFor(flagTokens) -> {
                        result.add(arg)
                        i += 1
                    }
                    else -> i += 1
                }
            }
            return result
        }

        private fun Set<String>.toFlagTokens(): Set<String> =
            mapTo(HashSet(size)) { "--$it" }

        private fun String.isKeyValueFor(flagTokens: Set<String>): Boolean =
            flagTokens.any { startsWith("$it=") }

        /**
         * Returns the path of the first task in [workGraph] that declares [optionName] as
         * `@Option(executionTimeOnly = true)`, or `null` if none can be found (e.g. when the
         * manifest is stale after a build-script change). [excludeTaskPath] is the path of
         * the violating task — it is skipped during the scan since it's known to declare the
         * option with the opposite semantics.
         */
        fun findExecutionTimeOnlyContributor(
            workGraph: ScheduledWork,
            optionReader: OptionReader,
            optionName: String,
            excludeTaskPath: String
        ): String? =
            workGraph.scheduledNodes
                .asSequence()
                .filterIsInstance<LocalTaskNode>()
                .map { it.task }
                .filter { it.path != excludeTaskPath }
                .firstOrNull { task -> declaresAsExecutionTimeOnly(task, optionName, optionReader) }
                ?.path

        private fun declaresAsExecutionTimeOnly(
            task: Task,
            optionName: String,
            optionReader: OptionReader
        ): Boolean {
            val descriptors = try {
                TaskOptionsGenerator.generate(task, optionReader).all
            } catch (_: OptionValidationException) {
                // Match the collector's policy: a task with malformed @Option metadata
                // can't have contributed to the manifest. Don't let other exception types
                // hide here — they'd surface as a misleading "stale manifest" error.
                return false
            }
            return descriptors.any { it.name == optionName && it.isExecutionTimeOnly }
        }
    }
}
