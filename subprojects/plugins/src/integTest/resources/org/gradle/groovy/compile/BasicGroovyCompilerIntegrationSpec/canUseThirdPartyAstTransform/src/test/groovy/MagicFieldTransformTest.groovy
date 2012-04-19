import org.junit.Test

@MagicField
class MagicFieldTransformTest {
    @Test
    void transformHasBeenApplied() {
        assert getClass().declaredFields.find { it.name == "magicField" }
        assert magicField == "magicValue"
    }
}
