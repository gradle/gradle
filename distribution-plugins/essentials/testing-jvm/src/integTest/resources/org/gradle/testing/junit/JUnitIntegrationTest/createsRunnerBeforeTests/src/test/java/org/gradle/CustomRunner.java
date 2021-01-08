package org.gradle;

import java.lang.reflect.Method;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class CustomRunner extends BlockJUnit4ClassRunner {
    public static boolean isClassUnderTestLoaded;
    private final Class<?> bootstrappedTestClass;

    public CustomRunner(Class<?> clazz) throws Exception {
        super(clazz);
        bootstrappedTestClass = clazz;
    }

    @Override
    protected Statement methodBlock(final FrameworkMethod method) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {

                if(isClassUnderTestLoaded){
                    throw new RuntimeException("Test Class should not be loaded");
                }

                final HelperTestRunner helperTestRunner = new HelperTestRunner(bootstrappedTestClass);
                final Method bootstrappedMethod = bootstrappedTestClass.getMethod(method.getName());
                final Statement statement = helperTestRunner.methodBlock(new FrameworkMethod(bootstrappedMethod));
                statement.evaluate();
            }
        };
    }

    public class HelperTestRunner extends BlockJUnit4ClassRunner {
        public HelperTestRunner(Class<?> testClass) throws InitializationError {
            super(testClass);
        }

        @Override
        protected Object createTest() throws Exception {
            return super.createTest();
        }

        @Override
        public Statement classBlock(RunNotifier notifier) {
            return super.classBlock(notifier);
        }

        @Override
        public Statement methodBlock(FrameworkMethod method) {
            return super.methodBlock(method);
        }
    }
}
