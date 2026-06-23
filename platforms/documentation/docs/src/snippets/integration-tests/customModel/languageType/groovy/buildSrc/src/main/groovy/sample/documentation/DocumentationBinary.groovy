package sample.documentation

import org.gradle.model.Managed
import org.gradle.platform.base.BinarySpec

// tag::binary-declaration[]
@Managed
interface DocumentationBinary extends BinarySpec {
    File getOutputDir()
    void setOutputDir(File outputDir)
}
// end::binary-declaration[]
