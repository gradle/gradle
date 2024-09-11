package ${packageName}

import org.junit.Assert._

class ${testClassName} {
    val production = new ${productionClassName}("value")

    @org.junit.Test
    def test() {
        assertEquals(production.property, "value")
    }
}