import org.junit.Test

@MagicField @MagicInterface
class MagicFieldTransformTest {
    @Test
    void transformHasBeenApplied() {
        assert getClass().declaredFields.find { it.name == "magicField" }
        assert magicField == "magicValue"
        assert this instanceof Marker
    }
}
