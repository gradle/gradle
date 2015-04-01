package org.gradle.api.tasks.application;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.plugins.StartScriptGenerator;
import org.gradle.api.internal.plugins.UnixStartScriptGenerator;
import org.gradle.api.internal.plugins.WindowsStartScriptGenerator;
import org.gradle.api.tasks.*;
import org.gradle.jvm.application.scripts.ScriptGenerator;
import org.gradle.util.GUtil;

import java.io.File;

/**
 * A {@link org.gradle.api.Task} for creating OS-specific start scripts.
 * It provides default implementations for generating start scripts for Unix and Windows runtime environments.
 * <p>
 * Example:
 * <pre autoTested=''>
 * task createStartScripts(type: CreateStartScripts) {
 *   outputDir = file('build/sample')
 *   mainClassName = 'org.gradle.test.Main'
 *   applicationName = 'myApp'
 *   classpath = files('path/to/some.jar')
 * }
 * </pre>
 * <p>
 * The standard start script generation logic can be changed by assigning custom start script generator classes that implement the interface {@link ScriptGenerator}.
 * {@code ScriptGenerator} requires you to implement a single method.
 * The parameter of type {@link org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails} represents the data e.g. classpath, application name.
 * The parameter of type {@code java.io.Writer} writes to the target start script file.
 * <p>
 * Example:
 * <pre autoTested=''>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
 *   windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
 * }
 *
 * class CustomUnixStartScriptGenerator implements ScriptGenerator {
 *   void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 *     // implementation
 *   }
 * }
 *
 * class CustomWindowsStartScriptGenerator implements ScriptGenerator {
 *   void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 *     // implementation
 *   }
 * }
 * </pre>
 * <p>
 * Providing a custom start script generator is powerful.
 * Sometimes changing the underlying template used for the script generation is good enough.
 * For that purpose the default implementations of {@link ScriptGenerator} also implement the interface {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}.
 * The method {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator#setTemplate(org.gradle.api.resources.TextResource)} can be used to provide a custom template.
 * Within the template files the following variables can be used:
 * <ul>
 * <li>{@code applicationName}</li>
 * <li>{@code optsEnvironmentVar}</li>
 * <li>{@code exitEnvironmentVar}</li>
 * <li>{@code mainClassName}</li>
 * <li>{@code defaultJvmOpts}</li>
 * <li>{@code appNameSystemProperty}</li>
 * <li>{@code appHomeRelativePath}</li>
 * <li>{@code classpath}</li>
 * <ul>
 * Example:
 * <p>
 * <pre>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator.template = resources.text.fromFile('customUnixStartScript.txt')
 *   windowsStartScriptGenerator.template = resources.text.fromFile('customWindowsStartScript.txt')
 * }
 * </pre>
 */
public class CreateStartScripts extends ConventionTask {

    private File outputDir;
    private String mainClassName;
    private Iterable<String> defaultJvmOpts = Lists.newLinkedList();
    private String applicationName;
    private String optsEnvironmentVar;
    private String exitEnvironmentVar;
    private FileCollection classpath;
    private ScriptGenerator unixStartScriptGenerator = new UnixStartScriptGenerator();
    private ScriptGenerator windowsStartScriptGenerator = new WindowsStartScriptGenerator();

    /**
     * The environment variable to use to provide additional options to the JVM.
     */
    @Input
    @Optional
    public String getOptsEnvironmentVar() {
        if (GUtil.isTrue(optsEnvironmentVar)) {
            return optsEnvironmentVar;
        }

        if (!GUtil.isTrue(getApplicationName())) {
            return null;
        }

        return GUtil.toConstant(getApplicationName()) + "_OPTS";
    }

    /**
     * The environment variable to use to control exit value (Windows only).
     */
    @Input
    @Optional
    public String getExitEnvironmentVar() {
        if (GUtil.isTrue(exitEnvironmentVar)) {
            return exitEnvironmentVar;
        }

        if (!GUtil.isTrue(getApplicationName())) {
            return null;
        }

        return GUtil.toConstant(getApplicationName()) + "_EXIT_CONSOLE";
    }

    /**
     * Returns the full path to the Unix script. The target directory is represented by the output directory, the file name is the application name without a file extension.
     */
    @OutputFile
    public File getUnixScript() {
        return new File(getOutputDir(), getApplicationName());
    }

    /**
     * Returns the full path to the Windows script. The target directory is represented by the output directory, the file name is the application name plus the file extension .bat.
     */
    @OutputFile
    public File getWindowsScript() {
        return new File(getOutputDir(), getApplicationName() + ".bat");
    }

    /**
     * The directory to write the scripts into.
     */
    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * The main classname used to start the Java application.
     */
    @Input
    public String getMainClassName() {
        return mainClassName;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    /**
     * The application's default JVM options. Defaults to an empty list.
     */
    @Input
    @Optional
    public Iterable<String> getDefaultJvmOpts() {
        return defaultJvmOpts;
    }

    public void setDefaultJvmOpts(Iterable<String> defaultJvmOpts) {
        this.defaultJvmOpts = defaultJvmOpts;
    }

    /**
     * The application's name.
     */
    @Input
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setOptsEnvironmentVar(String optsEnvironmentVar) {
        this.optsEnvironmentVar = optsEnvironmentVar;
    }

    public void setExitEnvironmentVar(String exitEnvironmentVar) {
        this.exitEnvironmentVar = exitEnvironmentVar;
    }

    /**
     * The class path for the application.
     */
    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * The Unix start script generator. Defaults to a standard implementation.
     */
    @Incubating
    public ScriptGenerator getUnixStartScriptGenerator() {
        return unixStartScriptGenerator;
    }

    public void setUnixStartScriptGenerator(ScriptGenerator unixStartScriptGenerator) {
        this.unixStartScriptGenerator = unixStartScriptGenerator;
    }

    /**
     * The Windows start script generator. Defaults to a standard implementation.
     */
    @Incubating
    public ScriptGenerator getWindowsStartScriptGenerator() {
        return windowsStartScriptGenerator;
    }

    public void setWindowsStartScriptGenerator(ScriptGenerator windowsStartScriptGenerator) {
        this.windowsStartScriptGenerator = windowsStartScriptGenerator;
    }

    @TaskAction
    public void generate() {
        StartScriptGenerator generator = new StartScriptGenerator(unixStartScriptGenerator, windowsStartScriptGenerator);
        generator.setApplicationName(getApplicationName());
        generator.setMainClassName(getMainClassName());
        generator.setDefaultJvmOpts(getDefaultJvmOpts());
        generator.setOptsEnvironmentVar(getOptsEnvironmentVar());
        generator.setExitEnvironmentVar(getExitEnvironmentVar());
        generator.setClasspath(Iterables.transform(getClasspath().getFiles(), new Function<File, String>() {
            @Override
            public String apply(File input) {
                return "lib/" + input.getName();
            }
        }));
        generator.setScriptRelPath("bin/" + getUnixScript().getName());
        generator.generateUnixScript(getUnixScript());
        generator.generateWindowsScript(getWindowsScript());
    }

}
