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

package org.gradle.kotlin.dsl.provider.capture

import org.gradle.api.logging.Logging
import java.nio.file.Path

private val logger = Logging.getLogger(CaptureMinimizer::class.java)

/**
 * Post-compile capture-minimization transform for Kotlin-DSL compiled scripts.
 *
 * Rewrites nested SAM lambda classes (Action/Spec/Runnable) so that a lambda capturing the compiled
 * script only to read/write top-level `val`/`var`s carries those (a `val` by value, a `var` via a
 * shared `kotlin.jvm.internal.Ref` cell) instead of the whole script (its `this$0` field). Nested
 * lambda chains are threaded inside-out. Everything not provably safe is left byte-identical.
 *
 * The pipeline is a sequence of pure phases, one per file: each reads the immutable [ClassesScope] (via
 * the specific collections it needs, plus the previous phase's result) and produces its own result,
 * rather than mutating shared state. A lambda is rewritten only if a sound dataflow
 * ([analyzeScriptReferenceFlowInLambdaMethod]) proves every use of its script reference is a top-level
 * val/var getter/setter or a thread into a rewritable inner lambda's constructor. Any escape,
 * inherited/other call, non-convertible var, non-trivial constructor, or malformed creation site makes
 * the whole lambda bail. Worst case is today's behavior.
 */
object CaptureMinimizer {

    /** Bumped whenever the transform's behavior changes, so cached script workspaces are invalidated. */
    const val VERSION = 1

    /**
     * Minimizes captures in the compiled classes under [classesDir], in place.
     *
     * @param classesDir directory of a single script compilation unit's `.class` files
     * @return the number of lambdas de-captured
     */
    fun minimize(classesDir: Path): Int {
        val scope = ClassesScope.readFromClassesDir(classesDir)
        val bodyAnalyses = analyzeBodies(scope.capturingLambdas, scope.scripts, scope.lambdaClassNames)
        val sites = scanLambdaCreationSites(scope.classes, scope.scripts.keys, scope.capturingLambdas.keys)
        val rewritable = decideRewritableLambdas(scope.capturingLambdas.keys, bodyAnalyses, sites)
        val plan = rewritePlan(rewritable, scope.capturingLambdas, bodyAnalyses, sites)
        return CaptureRewriter(scope, plan).rewriteAndWrite()
    }
}

/** Records, at debug level, that a lambda was left untouched and why — shared across the analysis phases. */
internal fun logSkip(lambdaName: InternalClassName, reason: String?) {
    logger.debug("Capture minimization skips {}: {}", lambdaName, reason)
}
