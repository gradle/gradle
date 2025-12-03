import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.SKIP_METADATA_VERSION_CHECK_PROPERTY_NAME
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import java.io.File
import kotlin.metadata.internal.metadata.deserialization.MetadataVersion


// TODO make this a gradle use home service

// TODO performance
//  cache per classpath entry
//      key: classpath entry hash
//      value: boolean

internal
fun checkAllMetadataInClasspath(compileOptions: KotlinCompilerOptions, classPath: ClassPath, classpathWalker: ClasspathWalker) {
    if (compileOptions.explicitSkipMetadataVersionCheck != null) {
        // If the flag is set explicitly, then we don't do any checking.
        // Either check results are intentionally suppressed (if the flag is set to 'true'),
        // or checking is unnecessary, because there will be compilation failures anyway (if the flag is set to 'false').
        return;
    }

    val extractor = KotlinMetadataVersionExtractor()
    classPath.asFiles.forEach { file ->
        var incompatibilityFoundInFile = false
        classpathWalker.visit(file) { entry ->
            if (!incompatibilityFoundInFile && entry.name.endsWith(".class")) {
                val classReader = ClassReader(entry.content)
                classReader.accept(extractor.reset(), ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                if (extractor.metadataVersion != null) {
                    val metadataVersion = MetadataVersion(extractor.metadataVersion!!, false)
                    val compatible = metadataVersion.isCompatibleWithCurrentCompilerVersion()
                    if (!compatible) {
                        issueDeprecationWarning(file)
                        incompatibilityFoundInFile = true
                    }
                }
            }
        }
    }
}

private
fun issueDeprecationWarning(file: File) {
    // TODO: use problems api instead of the deprecation logger?

    DeprecationLogger.deprecateBehaviour("Using incompatible Kotlin dependencies in scripts without setting the '$SKIP_METADATA_VERSION_CHECK_PROPERTY_NAME' property.")
        .withAdvice("""
            Using dependencies compiled with an incompatible Kotlin version has undefined behaviour and could lead to strange errors.
            
            Compatible Kotlin versions are:
                - the CURRENT version ($embeddedKotlinVersion)
                - the NEXT version
                - all PAST versions
            
            The incompatible dependency in question comes from ${file.absolutePath}.
            
            You have the following options:
                Solution 1: 
                    - remove the offending dependency
                    - optionally: set the property to false to enforce metadata check
                
                Solution 2: 
                    - set the property to 'true' to disable this warning
                    
            In Gradle 10, the property will default to 'false'.
            """.trimIndent()
        )
        .willBecomeAnErrorInGradle10()
        .undocumented() // TODO: do more?
        .nagUser()
}

class KotlinMetadataVersionExtractor : ClassVisitor(AsmConstants.ASM_LEVEL) {
    var metadataVersion: IntArray? = null

    private val annotationVisitor = MetadataAnnotationVisitor()

    fun reset(): KotlinMetadataVersionExtractor {
        metadataVersion = null
        return this
    }

    // Check every annotation on the class
    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        if ("Lkotlin/Metadata;" == descriptor) {
            return annotationVisitor
        }
        return super.visitAnnotation(descriptor, visible)
    }

    private inner class MetadataAnnotationVisitor : AnnotationVisitor(AsmConstants.ASM_LEVEL) {

        override fun visit(name: String?, value: Any?) {
            if (name == "mv" || name == "metadataVersion") {
                metadataVersion = value as IntArray?
            }
            super.visit(name, value)
        }
    }
}