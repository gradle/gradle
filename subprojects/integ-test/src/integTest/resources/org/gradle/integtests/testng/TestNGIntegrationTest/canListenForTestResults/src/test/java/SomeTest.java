public class SomeTest {
    @org.testng.annotations.Test
    public void pass() {
    }

    @org.testng.annotations.Test
    public void fail() {
        assert false;
    }

    @org.testng.annotations.Test
    public void knownError() {
        throw new RuntimeException("message");
    }

    @org.testng.annotations.Test
    public void unknownError() throws AppException {
        throw new AppException();
    }
}
