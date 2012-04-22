import org.junit.Test
import TestDelegate

class UseBuiltInTransformTest {
    @Delegate final TestDelegate delegate = new TestDelegate()

    @Test
    void transformHasBeenApplied() {
        assert doStuff("hi") == "[hi]"
    }
}
