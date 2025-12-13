import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.execution.KotlinMetadataCompatibilityChecker
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.SKIP_METADATA_VERSION_CHECK_PROPERTY_NAME
import java.io.File


internal
fun checkAllMetadataInClasspath(compileOptions: KotlinCompilerOptions, classPath: ClassPath, compatibilityChecker: KotlinMetadataCompatibilityChecker) {
    if (compileOptions.explicitSkipMetadataVersionCheck != null) {
        // If the flag is set explicitly, then we don't do any checking.
        // Either check results are intentionally suppressed (if the flag is set to 'true'),
        // or checking is unnecessary, because there will be compilation failures anyway (if the flag is set to 'false').
        return;
    }

    val incompatibleFilesInClasspath = compatibilityChecker.incompatibleClasspathElements(classPath)
    if (incompatibleFilesInClasspath.isNotEmpty()) {
        issueDeprecationWarning(incompatibleFilesInClasspath)
    }
}


private
fun issueDeprecationWarning(files: Iterable<File>) {
    DeprecationLogger.deprecateBehaviour("Using incompatible Kotlin dependencies in scripts without setting the '$SKIP_METADATA_VERSION_CHECK_PROPERTY_NAME' property.")
        .withAdvice(
"""Using dependencies compiled with an incompatible Kotlin version has undefined behaviour and could lead to strange errors.

Compatible Kotlin versions are:
    - the CURRENT version ($embeddedKotlinVersion)
    - the NEXT version
    - all PAST versions

Incompatible dependencies were found in: ${files.joinToString("\n    - ", "\n    - ", "") { file -> file.name }}.

You have the following options:
    Solution 1: 
        - remove the offending dependency
        - optionally: set the property to false to enforce metadata check
    
    Solution 2: 
        - set the property to 'true' to disable this warning
        
In Gradle 10, the property will default to 'false'."""
        )
        .willBecomeAnErrorInGradle10()
        .undocumented()
        .nagUser()
}