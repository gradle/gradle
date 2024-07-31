/*
 * Copyright 2007-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.tasks.compile;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Compilation options to be passed to the Groovy compiler.
 */
@SuppressWarnings("deprecation")
public abstract class GroovyCompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private GroovyForkOptions forkOptions = getObjectFactory().newInstance(GroovyForkOptions.class);
    private final SetProperty<String> disabledGlobalASTTransformations = getObjectFactory().setProperty(String.class);

    public GroovyCompileOptions() {
        getFailOnError().convention(true);
        getVerbose().convention(false);
        getListFiles().convention(false);
        getEncoding().convention(StandardCharsets.UTF_8.name());
        getFork().convention(true);
        getJavaAnnotationProcessing().convention(false);
        getParameters().convention(false);
        getFileExtensions().convention(ImmutableList.of("java", "groovy"));
        getKeepStubs().convention(false);
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Tells whether the compilation task should fail if compile errors occurred. Defaults to {@code true}.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getFailOnError();

    @Internal
    @Deprecated
    public Property<Boolean> getIsFailOnError() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsFailOnError()", "getFailOnError()");
        return getFailOnError();
    }

    /**
     * Tells whether to turn on verbose output. Defaults to {@code false}.
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getVerbose();

    @Internal
    @Deprecated
    public Property<Boolean> getIsVerbose() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsVerbose()", "getVerbose()");
        return getVerbose();
    }

    /**
     * Tells whether to print which source files are to be compiled. Defaults to {@code false}.
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getListFiles();

    @Internal
    @Deprecated
    public Property<Boolean> getIsListFiles() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsListFiles()", "getListFiles()");
        return getListFiles();
    }

    /**
     * Tells the source encoding. Defaults to {@code UTF-8}.
     */
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getEncoding();

    /**
     * Tells whether to run the Groovy compiler in a separate process. Defaults to {@code true}.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getFork();

    @Internal
    @Deprecated
    public Property<Boolean> getIsFork() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsFork()", "getFork()");
        return getFork();
    }

    /**
     * A Groovy script file that configures the compiler, allowing extensive control over how the code is compiled.
     * <p>
     * The script is executed as Groovy code, with the following context:
     * </p>
     * <ul>
     * <li>The instance of <a href="https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/CompilerConfiguration.html">CompilerConfiguration</a> available as the {@code configuration} variable.</li>
     * <li>All static members of <a href="https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/customizers/builder/CompilerCustomizationBuilder.html">CompilerCustomizationBuilder</a> pre imported.</li>
     * </ul>
     * <p>
     * This facilitates the following pattern:
     * </p>
     * <pre>
     * withConfig(configuration) {
     *   // use compiler configuration DSL here
     * }
     * </pre>
     * <p>
     * For example, to activate type checking for all Groovy classes…
     * </p>
     * <pre>
     * import groovy.transform.TypeChecked
     *
     * withConfig(configuration) {
     *     ast(TypeChecked)
     * }
     * </pre>
     * <p>
     * Please see <a href="https://docs.groovy-lang.org/latest/html/documentation/#compilation-customizers">the Groovy compiler customization builder documentation</a>
     * for more information about the compiler configuration DSL.
     * </p>
     * <p>
     * <b>This feature is only available if compiling with Groovy 2.1 or later.</b>
     * </p>
     * @see <a href="https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/CompilerConfiguration.html">CompilerConfiguration</a>
     * @see <a href="https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/customizers/builder/CompilerCustomizationBuilder.html">CompilerCustomizationBuilder</a>
     */
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    @ReplacesEagerProperty
    public abstract RegularFileProperty getConfigurationScript();

    /**
     * Whether the Groovy code should be subject to Java annotation processing.
     * <p>
     * Annotation processing of Groovy code works by having annotation processors visit the Java stubs generated by the
     * Groovy compiler in order to support joint compilation of Groovy and Java source.
     * <p>
     * When set to {@code true}, stubs will be unconditionally generated for all Groovy sources, and Java annotations processors will be executed on those stubs.
     * <p>
     * When this option is set to {@code false} (the default), Groovy code will not be subject to annotation processing, but any joint compiled Java code will be.
     * If the compiler argument {@code "-proc:none"} was specified as part of the Java compile options, the value of this flag will be ignored.
     * No annotation processing will be performed regardless, on Java or Groovy source.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getJavaAnnotationProcessing();

    @Internal
    @Deprecated
    public Property<Boolean> getIsJavaAnnotationProcessing() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsJavaAnnotationProcessing()", "getJavaAnnotationProcessing()");
        return getJavaAnnotationProcessing();
    }

    /**
     * Whether the Groovy compiler generate metadata for reflection on method parameter names on JDK 8 and above.
     *
     * @since 6.1
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getParameters();

    @Internal
    @Deprecated
    public Property<Boolean> getIsParameters() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsParameters()", "getParameters()");
        return getParameters();
    }

    /**
     * Returns options for running the Groovy compiler in a separate process. These options only take effect
     * if {@code fork} is set to {@code true}.
     */
    @Nested
    public GroovyForkOptions getForkOptions() {
        return forkOptions;
    }

    /**
     * Sets options for running the Groovy compiler in a separate process. These options only take effect
     * if {@code fork} is set to {@code true}.
     *
     * @deprecated Setting a new instance of this property is unnecessary. This method will be removed in Gradle 9.0. Use {@link #forkOptions(Action)} instead.
     */
    @Deprecated
    public void setForkOptions(GroovyForkOptions forkOptions) {
        DeprecationLogger.deprecateMethod(GroovyCompileOptions.class, "setForkOptions(GroovyForkOptions)")
            .replaceWith("forkOptions(Action)")
            .withContext("Setting a new instance of forkOptions is unnecessary.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_nested_properties_setters")
            .nagUser();
        this.forkOptions = forkOptions;
    }

    /**
     * Execute the given action against {@link #getForkOptions()}.
     *
     * @since 8.11
     */
    public void forkOptions(Action<? super GroovyForkOptions> action) {
        action.execute(forkOptions);
    }

    /**
     * Returns optimization options for the Groovy compiler. Allowed values for an option are {@code true} and {@code false}.
     * Only takes effect when compiling against Groovy 1.8 or higher.
     *
     * <p>Known options are:
     *
     * <dl>
     *     <dt>indy
     *     <dd>Use the invokedynamic bytecode instruction. Requires JDK7 or higher and Groovy 2.0 or higher. Disabled by default.
     *     <dt>int
     *     <dd>Optimize operations on primitive types (e.g. integers). Enabled by default.
     *     <dt>all
     *     <dd>Enable or disable all optimizations. Note that some optimizations might be mutually exclusive.
     * </dl>
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract MapProperty<String, Boolean> getOptimizationOptions();

    /**
     * Returns the set of global AST transformations which should not be loaded into the Groovy compiler.
     *
     * @see <a href="https://docs.groovy-lang.org/latest/html/api/org/codehaus/groovy/control/CompilerConfiguration.html#setDisabledGlobalASTTransformations(java.util.Set)">CompilerConfiguration</a>
     * @since 7.4
     */
    @Input
    public SetProperty<String> getDisabledGlobalASTTransformations() {
        return disabledGlobalASTTransformations;
    }
    /**
     * Returns the directory where Java stubs for Groovy classes will be stored during Java/Groovy joint
     * compilation. Defaults to {@code null}, in which case a temporary directory will be used.
     */
    @Internal
    @ReplacesEagerProperty
    // TOOD:LPTR Should be just a relative path
    public abstract DirectoryProperty getStubDir();

    /**
     * Returns the list of acceptable source file extensions. Only takes effect when compiling against
     * Groovy 1.7 or higher. Defaults to {@code ImmutableList.of("java", "groovy")}.
     */
    @Input
    @ReplacesEagerProperty
    public abstract ListProperty<String> getFileExtensions();

    /**
     * Tells whether Java stubs for Groovy classes generated during Java/Groovy joint compilation
     * should be kept after compilation has completed. Useful for joint compilation debugging purposes.
     * Defaults to {@code false}.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getKeepStubs();

    @Internal
    @Deprecated
    public Property<Boolean> getIsKeepStubs() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsKeepStubs()", "getKeepStubs()");
        return getKeepStubs();
    }

    /**
     * Convenience method to set {@link GroovyForkOptions} with named parameter syntax.
     * Calling this method will set {@code fork} to {@code true}.
     *
     * @deprecated This method will be removed in Gradle 9.0
     */
    @Deprecated
    public GroovyCompileOptions fork(Map<String, Object> forkArgs) {

        DeprecationLogger.deprecateMethod(GroovyCompileOptions.class, "fork(Map)")
            .withAdvice("Set properties directly on the 'forkOptions' property instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_abstract_options")
            .nagUser();

        getFork().set(true);

        // Disable deprecation to avoid double-warning
        DeprecationLogger.whileDisabled(() -> forkOptions.define(forkArgs));
        return this;
    }
}
