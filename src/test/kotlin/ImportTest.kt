import analysis.AnalysisContext
import analysis.DataObjectResolverImpl
import analysis.ErrorReason
import analysis.ResolutionError
import com.h0tk3y.kotlin.staticObjectNotation.AccessChain
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.Import
import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import kotlinx.ast.common.ast.Ast
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportTest {
    private val resolver = DataObjectResolverImpl()

    private val ast = object : Ast {
        override val description: String = "test"
    }
    
    private fun testContext(errors: MutableList<ResolutionError>): AnalysisContext {
        return AnalysisContext(
            AnalysisSchema(
                DataType.DataClass(Unit::class, emptyList(), emptyList(), emptyList()),
                emptyMap(), 
                emptyMap(),
                emptyMap(),
                emptySet()
            ),
            emptyMap(),
            errors::add
        )
    }
    
    @Test
    fun `collects basic imports`() {
        val imports = listOf(
            Import(AccessChain(nameParts = listOf("a", "b", "C"), originAst = ast), originAst = ast),
            Import(AccessChain(nameParts = listOf("a", "b", "c", "d"), originAst = ast), originAst = ast)
        )
        val errors = mutableListOf<ResolutionError>()
        val result = resolver.collectImports(imports, testContext(errors))

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
                "d" to FqName("a.b.c", "d")
            ),
            result
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `reports ambiguous import errors`() {
        val imports = listOf(
            Import(AccessChain(nameParts = listOf("a", "b", "C"), originAst = ast), originAst = ast),
            Import(AccessChain(nameParts = listOf("a", "c", "C"), originAst = ast), originAst = ast),
            Import(AccessChain(nameParts = listOf("a", "b", "D"), originAst = ast), originAst = ast)
        )
        val errors = mutableListOf<ResolutionError>()
        val result = resolver.collectImports(imports, testContext(errors))

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
                "D" to FqName("a.b", "D"),
            ),
            result
        )
        assertEquals(
            listOf(ResolutionError(imports[1], ErrorReason.AmbiguousImport(FqName("a.c", "C")))),
            errors
        )
    }

    @Test
    fun `does not report errors on the same import appearing more than once`() {
        val imports = listOf(
            Import(AccessChain(nameParts = listOf("a", "b", "C"), originAst = ast), originAst = ast),
            Import(AccessChain(nameParts = listOf("a", "b", "C"), originAst = ast), originAst = ast),
            Import(AccessChain(nameParts = listOf("a", "b", "C"), originAst = ast), originAst = ast),
        )
        val errors = mutableListOf<ResolutionError>()
        val result = resolver.collectImports(imports, testContext(errors))

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
            ),
            result
        )
        assertTrue(errors.isEmpty())
    }
}