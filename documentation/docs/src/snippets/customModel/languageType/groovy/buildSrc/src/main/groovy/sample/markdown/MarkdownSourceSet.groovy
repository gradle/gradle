package sample.markdown

import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.Managed

// tag::markdown-lang-declaration[]
@Managed
interface MarkdownSourceSet extends LanguageSourceSet {
    boolean isGenerateIndex()
    void setGenerateIndex(boolean generateIndex)

    boolean isSmartQuotes()
    void setSmartQuotes(boolean smartQuotes)
}
// end::markdown-lang-declaration[]
