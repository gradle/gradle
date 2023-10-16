package com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaTypeRefContext
import com.h0tk3y.kotlin.staticObjectNotation.demo.assignmentTrace
import com.h0tk3y.kotlin.staticObjectNotation.demo.prettyStringFromReflection
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ObjectReflection
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ReflectionContext

fun AnalysisSchema.reflect(code: String): ObjectReflection {
    val resolution = resolve(code)
    val trace = assignmentTrace(resolution)
    val context = ReflectionContext(SchemaTypeRefContext(this), resolution, trace)
    val topLevel = com.h0tk3y.kotlin.staticObjectNotation.objectGraph.reflect(resolution.topLevelReceiver, context)
    
    return topLevel
}

fun printReflection(objectReflection: ObjectReflection) {
    println(prettyStringFromReflection(objectReflection))
}

fun AnalysisSchema.reflectAndPrint(code: String) {
    println(prettyStringFromReflection(reflect(code)))
}