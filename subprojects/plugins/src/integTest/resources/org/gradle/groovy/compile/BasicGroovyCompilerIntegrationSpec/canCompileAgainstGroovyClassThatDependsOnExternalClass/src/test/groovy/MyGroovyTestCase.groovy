// GroovyTestCase depends on external class junit.framework.TestCase
class MyGroovyTestCase extends GroovyTestCase {
    void testSomething() {
        assert getName() == "testSomething"
    }
}