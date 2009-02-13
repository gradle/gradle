package org.gradle.integtests.testng;

import org.gradle.integtests.AbstractIntegrationTest;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.GroovyPlugin;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.endsWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestNGIntegrationTest extends AbstractIntegrationTest {
    protected class CompileType{
        private String name;
        private String pluginClass;
        private String dependency;

        public CompileType(String name, String pluginClass) {
            this(name, pluginClass, null);
        }

        public CompileType(String name, String pluginClass, String dependency) {
            this.name = name;
            this.pluginClass = pluginClass;
            this.dependency = dependency;
        }

        public String getName() {
            return name;
        }

        public String getPluginClass() {
            return pluginClass;
        }

        public String getCompileDependency() {
            if ( dependency == null )
                return null;
            else
                return "\"" + dependency + "\"";
        }
    }
    public final CompileType JAVA_TYPE = new CompileType("java", JavaPlugin.class.getName());
    public final CompileType GROOVY_TYPE = new CompileType("groovy", "'groovy'", "org.codehaus.groovy:groovy-all:1.5.6");

    private String testngConfiguration;

    protected AbstractTestNGIntegrationTest(String testngConfiguration)
    {
        this.testngConfiguration = testngConfiguration;
    }

    protected GradleExecutionResult doPassingTest(TestFile buildFile, CompileType compileType, boolean testReport)
    {
        writeTestNGbuildFile(buildFile, compileType, testReport);

        // just to have something in source
        writeClass(compileType);

        // passing test case
        writePassingTestClass(compileType);

        final GradleExecutionResult result = usingBuildFile(buildFile).runTasks("test");

        result.assertTasksExecuted(":init", ":resources", ":compile", ":testResources", ":testCompile", ":test");

        return result;
    }

    protected GradleExecutionFailure doFailingTest(TestFile buildFile, CompileType compileType, boolean testReport)
    {
        writeTestNGbuildFile(buildFile, compileType, testReport);

        // just to have something in source
        writeClass(compileType);

        // passing test case
        writeFailingTestClass(compileType);

        final GradleExecutionFailure failureResult = usingBuildFile(buildFile).runTasksAndExpectFailure("test");

        failureResult.getFailure().printStackTrace();

        failureResult.assertContext(endsWith("Execution failed for task ':test'."));
        failureResult.assertDescription(startsWith("There were failing tests. See the report at "));

        return failureResult;
    }

    private void writeTestNGbuildFile(AbstractIntegrationTest.TestFile buildFile, AbstractTestNGIntegrationTest.CompileType type, boolean testReport)
    {
        List<String> lines = new ArrayList<String>();

        lines.addAll(Arrays.asList(
            "import org.gradle.api.dependencies.Artifact",
            "usePlugin("+type.getPluginClass()+")",
            "version='0.1'",
            "sourceCompatibility=1.5",
            "targetCompatibility=1.5",
            "dependencies { ",
                "File libDir = new File(rootDir, '../lib')",
            "   addFlatDirResolver('lib',libDir).addArtifactPattern(new File(libDir.absolutePath, '[artifact](-[revision])(-[conf]).[ext]').absolutePath) "
        ));
        if ( type == GROOVY_TYPE ) {
                lines.addAll(Arrays.asList(
                    "compile " + type.getCompileDependency() //,
                    //"groovy \"org.codehaus.groovy:groovy-all:1.5.6\", \":ant-nodeps:1.7.0@jar\",\":ant:1.7.0@jar\",\":ant-junit:1.7.0@jar\", \":ant-launcher:1.7.0@jar\",\":gradle:0.5@jar\""
                ));
        }
        lines.addAll(Arrays.asList(
                "clientModule(['testCompile'],'testng:testng:5.8:jdk15') {",
                    "Artifact testngArtifact = new Artifact('testng','jar','jar',null, null)",
                    "testngArtifact.setConfs(['"+testngConfiguration+"'])",
                    "addArtifact(testngArtifact)",
                "}",
            "}"
        ));
        

        lines.addAll(writeBuildFileTestTask(testReport));

        buildFile.writelns(lines);

    }

    protected abstract List<String> writeBuildFileTestTask(boolean testReport);

    private void writeClass(AbstractTestNGIntegrationTest.CompileType type)
    {
        testFile("src/main/"+type.getName()+"/org/gradle/Ok."+type.getName()).writelns(
            "package org.gradle;",
            "public class Ok{",
            " String test = \"dummy\";",
            "}"
        );
    }

    protected abstract void writePassingTestClass(CompileType compileType);

    protected abstract void writeFailingTestClass(CompileType compileType);

}
