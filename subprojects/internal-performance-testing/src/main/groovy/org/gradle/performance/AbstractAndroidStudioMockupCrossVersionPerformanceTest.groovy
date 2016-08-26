package org.gradle.performance

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.gradle.performance.fixture.TestProjectLocator

/**
 * This is the base class for performance tests aimed at simulating what happens in Android Studio. In particular, it will
 * use the Tooling API to synchronize project or run builds.
 *
 * Those tests support custom models, which are found in the internalAndroidPerformanceTesting project. Individual tests
 * need to extend this class and tell what is the name of the class that will query the model and the name of the method.
 * This method will be passed a {@link org.gradle.tooling.ProjectConnection} instance.
 *
 */
@CompileStatic
public abstract class AbstractAndroidStudioMockupCrossVersionPerformanceTest extends AbstractToolingApiCrossVersionPerformanceTest {

    void experiment(String projectName, String displayName, @DelegatesTo(ToolingApiExperimentSpec) Closure<?> spec) {
        experimentSpec = new AndroidStudioExperimentSpec(displayName, projectName, temporaryFolder.testDirectory, 3, 10, 5000L, 500L, null)
        ((AndroidStudioExperimentSpec) experimentSpec).test = this
        def clone = spec.rehydrate(experimentSpec, this, this)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone.call(experimentSpec)
    }

    @InheritConstructors
    public static class AndroidStudioExperimentSpec extends org.gradle.performance.AbstractToolingApiCrossVersionPerformanceTest.ToolingApiExperimentSpec {
        AbstractAndroidStudioMockupCrossVersionPerformanceTest test

        void action(String className, String methodName) {
            action {
                test.tapiClassLoader.loadClass(className).invokeMethod(methodName, it)
            }
        }

        @Override
        List<File> getExtraTestClassPath() {
            def testProjectLocator = new TestProjectLocator()
            def projectDir = testProjectLocator.findProjectDir(projectName)
            def classpathFile = new File(projectDir, 'tapi-classpath.txt')
            if (!classpathFile.exists()) {
                throw new IllegalStateException("Cannot find the TAPI classpath file at $classpathFile. Make sure the template contains it.")
            }
            (classpathFile as String[]).findAll { it }.collect { new File(it) }
        }
    }

}
