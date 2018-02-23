import org.junit.runner.Description;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

public class CustomRunner extends BlockJUnit4ClassRunner {

    public CustomRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    /**
     * Returns a test Description with a null TestClass.
     * @param method method under test
     * @return a Description
     */
    @Override
    protected Description describeChild(FrameworkMethod method) {
        return Description.createTestDescription("Not a real class name", testName(method), "someSerializable");
    }
}
