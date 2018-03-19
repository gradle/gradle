package org.gradle.gradlebuild.packaging.shading

import org.gradle.internal.exceptions.Contextual

@Contextual
class ClassAnalysisException(message: String, cause: Throwable) : RuntimeException(message, cause)
