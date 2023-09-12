import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.DefaultLanguageTreeBuilder
import com.h0tk3y.kotlin.staticObjectNotation.ElementOrFailureResult
import com.h0tk3y.kotlin.staticObjectNotation.LanguageTreeResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.LanguageTreeBuilderWithTopLevelBlock
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.parseToAst
import org.intellij.lang.annotations.Language

internal fun parse(@Language("kts") code: String): List<ElementOrFailureResult<*>> {
    val ast = parseToAst(code)
    val defaultLanguageTreeBuilder = DefaultLanguageTreeBuilder()
    return ast.flatMap { defaultLanguageTreeBuilder.build(it).results }
}

internal fun parseToLanguage(@Language("kts") code: String): LanguageTreeResult {
    val ast = parseToAst(code)
    val builder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())
    return builder.build(ast.single())
}