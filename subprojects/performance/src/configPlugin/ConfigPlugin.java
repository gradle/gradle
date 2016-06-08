import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import java.io.File;

public class ConfigPlugin implements Plugin<Project> {
    public void apply(Project project) {
        for (Project child : project.getSubprojects()) {
            child.getPlugins().apply("java");
            child.getPlugins().apply("eclipse");
            child.getPlugins().apply("idea");
            child.getPlugins().apply(org.gradle.api.plugins.JavaPlugin.class);
            child.getPlugins().apply(org.gradle.plugins.ide.eclipse.EclipsePlugin.class);
            child.getPlugins().apply(org.gradle.plugins.ide.idea.IdeaPlugin.class);
            child.getRepositories().mavenCentral();
            child.getDependencies().add("compile", "commons-lang:commons-lang:2.5");
            child.getDependencies().add("compile", "commons-httpclient:commons-httpclient:3.0");
            child.getDependencies().add("compile", "commons-codec:commons-codec:1.2");
            child.getDependencies().add("compile", "org.slf4j:jcl-over-slf4j:1.6.6");
            child.getDependencies().add("compile", "org.codehaus:groovy:groovy-all:2.4.7");
            child.getDependencies().add("compile", "commons-codec:commons-codec:1.2");
            child.getDependencies().add("testCompile", "junit:junit:4.12");
            child.getDependencies().add("testCompile", "org.testng:testng:6.4");
            child.getDependencies().add("runtime", "com.googlecode:reflectasm:1.01");
            Test test = (Test)(child.getTasks().getByName("test"));
            test.jvmArgs("-XX:MaxPermSize=512m", "-XX:+HeapDumpOnOutOfMemoryError");
            child.getTasks().add("dependencyReport", DependencyReportTask.class).setOutputFile(new File(child.getBuildDir(), "dependencies.txt"));
        }
    }
}
