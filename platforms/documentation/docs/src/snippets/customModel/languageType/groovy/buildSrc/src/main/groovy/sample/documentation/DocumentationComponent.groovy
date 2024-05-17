package sample.documentation

import org.gradle.model.Managed
import org.gradle.platform.base.GeneralComponentSpec

// tag::component-declaration[]
@Managed
interface DocumentationComponent extends GeneralComponentSpec {}
// end::component-declaration[]
