package org.gradle.client.core.gradle.dcl

import org.gradle.client.build.model.ResolvedDomPrerequisites
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator

fun analyzer(
    prerequisites: ResolvedDomPrerequisites
): SimpleAnalysisEvaluator =
    SimpleAnalysisEvaluator.withSchema(
        prerequisites.settingsInterpretationSequence,
        prerequisites.projectInterpretationSequence
    )