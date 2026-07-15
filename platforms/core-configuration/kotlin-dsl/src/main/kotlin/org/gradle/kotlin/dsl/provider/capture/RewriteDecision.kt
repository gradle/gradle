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

// ---- decide rewritability (fixpoint) -> the set of rewritable lambdas ----

/**
 * A lambda is rewritable when its own body uses the script as data only, all its creation sites are
 * clean, and — transitively — every lambda it depends on is rewritable too. That last condition makes
 * this a monotone fixpoint: start optimistic, then revoke until the set stabilizes.
 */
internal fun decideRewritableLambdas(
    lambdaNames: Set<InternalClassName>,
    bodyAnalyses: Map<InternalClassName, LambdaBodyAnalysis>,
    sites: Map<InternalClassName, LambdaSites>,
): Set<InternalClassName> {
    val rewritable = lambdaNames.filterTo(mutableSetOf()) { name ->
        bodyAnalyses.getValue(name).usesScriptAsDataOnly && sites.getValue(name).allSitesClean
    }
    var changed = true
    while (changed) {
        changed = false
        for (name in rewritable.toList()) {
            if (name in rewritable && !dependenciesRewritable(name, bodyAnalyses, sites, rewritable)) {
                rewritable.remove(name)
                changed = true
            }
        }
    }
    return rewritable
}

private fun dependenciesRewritable(
    name: InternalClassName,
    bodyAnalyses: Map<InternalClassName, LambdaBodyAnalysis>,
    sites: Map<InternalClassName, LambdaSites>,
    rewritable: Set<InternalClassName>,
): Boolean {
    // Every inner lambda it threads the script into must itself be rewritable.
    bodyAnalyses.getValue(name).threaded.firstOrNull { it !in rewritable }?.let { inner ->
        logSkip(name, "inner $inner not rewritable")
        return false
    }
    // Every lambda-context creator must be rewritable, so its creation site is fixed consistently.
    sites.getValue(name).sites.firstOrNull { !it.isInImmediateScriptClass && it.inOwnerClass.internalName !in rewritable }?.let { site ->
        logSkip(name, "creator ${site.inOwnerClass.name} not rewritable")
        return false
    }
    return true
}

// ---- assemble the rewrite plan (folds each lambda's captured order) ----

internal fun rewritePlan(
    rewritable: Set<InternalClassName>,
    capturingLambdas: Map<InternalClassName, Lambda>,
    bodyAnalyses: Map<InternalClassName, LambdaBodyAnalysis>,
    sites: Map<InternalClassName, LambdaSites>,
): RewritePlan {
    val rewritableLambdas = rewritable.map { name ->
        RewritableLambda(
            lambda = capturingLambdas.getValue(name),
            capturedOrder = foldCapturedFields(name, bodyAnalyses),
            accessesByMethod = bodyAnalyses.getValue(name).accessesByMethod,
            sites = sites.getValue(name).sites,
        )
    }
    return RewritePlan(rewritableLambdas)
}

/** A lambda carries its own captured fields plus those of every inner lambda it threads the script into. */
private fun foldCapturedFields(name: InternalClassName, bodyAnalyses: Map<InternalClassName, LambdaBodyAnalysis>): List<String> {
    val captured = sortedSetOf<String>() // sorted -> canonical order used at the ctor and at every push site
    val visited = mutableSetOf<InternalClassName>()
    fun collect(current: InternalClassName) {
        if (!visited.add(current)) return
        val analysis = bodyAnalyses[current] ?: return
        captured += analysis.capturedFields
        analysis.threaded.forEach { collect(it) }
    }
    collect(name)
    return captured.toList()
}
