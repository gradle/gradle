package sample.documentation

import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.Managed

// tag::text-lang-declaration[]
@Managed
interface TextSourceSet extends LanguageSourceSet {}
// end::text-lang-declaration[]
