import org.junit.Test

@GroovyMagicField
class GroovyMagicFieldTransformTest {
    @Test
    void transformHasBeenApplied() {
        assert getClass().declaredFields.find { it.name == "magicField" }
        assert magicField == "magicValue"
    }
}
