package org.gradle.internal.declarativedsl.demo.reflection

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.demo.propertyLinkTrace
import org.gradle.internal.declarativedsl.demo.prettyStringFromReflection
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection
import org.gradle.internal.declarativedsl.objectGraph.ReflectionContext


fun AnalysisSchema.reflect(code: String): ObjectReflection {
    val resolution = resolve(code)
    val trace = propertyLinkTrace(resolution)
    val context = ReflectionContext(SchemaTypeRefContext(this), trace.resolvedPropertyLinksResolutionResult)
    val topLevel = org.gradle.internal.declarativedsl.objectGraph.reflect(resolution.topLevelReceiver, context)

    return topLevel
}


fun printReflection(objectReflection: ObjectReflection) {
    println(prettyStringFromReflection(objectReflection))
}


fun AnalysisSchema.reflectAndPrint(code: String) {
    println(prettyStringFromReflection(reflect(code)))
}
