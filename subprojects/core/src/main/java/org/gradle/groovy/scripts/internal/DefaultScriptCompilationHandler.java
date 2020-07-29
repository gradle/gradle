/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyResourceLoader;
import groovy.lang.Script;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.configuration.ImportsReader;
import org.gradle.groovy.scripts.ScriptCompilationException;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classloader.ImplementationHashAware;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;

public class DefaultScriptCompilationHandler implements ScriptCompilationHandler {
    private final Logger logger = LoggerFactory.getLogger(DefaultScriptCompilationHandler.class);
    private static final NoOpGroovyResourceLoader NO_OP_GROOVY_RESOURCE_LOADER = new NoOpGroovyResourceLoader();
    private static final String METADATA_FILE_NAME = "metadata.bin";
    private static final int EMPTY_FLAG = 1;
    private static final int HAS_METHODS_FLAG = 2;

    private final Deleter deleter;
    private final Map<String, List<String>> simpleNameToFQN;

    public DefaultScriptCompilationHandler(Deleter deleter, ImportsReader importsReader) {
        this.deleter = deleter;
        this.simpleNameToFQN = importsReader.getSimpleNameToFullClassNamesMapping();
    }

    @Override
    public void compileToDir(ScriptSource source, ClassLoader classLoader, File classesDir, File metadataDir, CompileOperation<?> extractingTransformer,
                             Class<? extends Script> scriptBaseClass, Action<? super ClassNode> verifier) {
        Timer clock = Time.startTimer();
        try {
            deleter.ensureEmptyDirectory(classesDir);
        } catch (IOException ioex) {
            throw new UncheckedIOException(ioex);
        }
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        configuration.setTargetDirectory(classesDir);
        try {
            compileScript(source, classLoader, configuration, metadataDir, extractingTransformer, verifier);
        } catch (Exception e) {
            try {
                deleter.deleteRecursively(classesDir);
                deleter.deleteRecursively(metadataDir);
            } catch (IOException ioex) {
                throw new UncheckedIOException(ioex);
            }
            throw e;
        }

        logger.debug("Timing: Writing script to cache at {} took: {}", classesDir.getAbsolutePath(), clock.getElapsed());
    }

    private void compileScript(ScriptSource source, ClassLoader classLoader, CompilerConfiguration configuration, File metadataDir,
                               final CompileOperation<?> extractingTransformer, final Action<? super ClassNode> customVerifier) {
        final Transformer transformer = extractingTransformer != null ? extractingTransformer.getTransformer() : null;
        logger.info("Compiling {} using {}.", source.getDisplayName(), transformer != null ? transformer.getClass().getSimpleName() : "no transformer");

        final EmptyScriptDetector emptyScriptDetector = new EmptyScriptDetector();
        final PackageStatementDetector packageDetector = new PackageStatementDetector();
        GroovyClassLoader groovyClassLoader = new GroovyClassLoader(classLoader, configuration, false) {
            @Override
            protected CompilationUnit createCompilationUnit(CompilerConfiguration compilerConfiguration,
                                                            CodeSource codeSource) {

                CompilationUnit compilationUnit = new CustomCompilationUnit(compilerConfiguration, codeSource, customVerifier, this);

                if (transformer != null) {
                    transformer.register(compilationUnit);
                }

                compilationUnit.addPhaseOperation(packageDetector, Phases.CANONICALIZATION);
                compilationUnit.addPhaseOperation(emptyScriptDetector, Phases.CANONICALIZATION);
                return compilationUnit;
            }
        };

        groovyClassLoader.setResourceLoader(NO_OP_GROOVY_RESOURCE_LOADER);
        String scriptText = source.getResource().getText();
        String scriptName = source.getClassName();
        GroovyCodeSource codeSource = new GroovyCodeSource(scriptText == null ? "" : scriptText, scriptName, "/groovy/script");
        try {
            try {
                groovyClassLoader.parseClass(codeSource, false);
            } catch (MultipleCompilationErrorsException e) {
                wrapCompilationFailure(source, e);
            } catch (CompilationFailedException e) {
                throw new GradleException(String.format("Could not compile %s.", source.getDisplayName()), e);
            }

            if (packageDetector.hasPackageStatement) {
                throw new UnsupportedOperationException(String.format("%s should not contain a package statement.", source.getLongDisplayName().getCapitalizedDisplayName()));
            }
            serializeMetadata(source, extractingTransformer, metadataDir, emptyScriptDetector.isEmptyScript(), emptyScriptDetector.getHasMethods());
        } finally {
            ClassLoaderUtils.tryClose(groovyClassLoader);
        }
    }

    private <M> void serializeMetadata(ScriptSource scriptSource, CompileOperation<M> extractingTransformer, File metadataDir, boolean emptyScript, boolean hasMethods) {
        File metadataFile = new File(metadataDir, METADATA_FILE_NAME);
        try {
            GFileUtils.mkdirs(metadataDir);
            try (KryoBackedEncoder encoder = new KryoBackedEncoder(new FileOutputStream(metadataFile))) {
                byte flags = (byte) ((emptyScript ? EMPTY_FLAG : 0) | (hasMethods ? HAS_METHODS_FLAG : 0));
                encoder.writeByte(flags);
                if (extractingTransformer != null && extractingTransformer.getDataSerializer() != null) {
                    Serializer<M> serializer = extractingTransformer.getDataSerializer();
                    serializer.write(encoder, extractingTransformer.getExtractedData());
                }
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Failed to serialize script metadata extracted for %s", scriptSource.getDisplayName()), e);
        }
    }

    private void wrapCompilationFailure(ScriptSource source, MultipleCompilationErrorsException e) {
        // Fix the source file name displayed in the error messages
        for (Object message : e.getErrorCollector().getErrors()) {
            if (message instanceof SyntaxErrorMessage) {
                try {
                    SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
                    Field sourceField = SyntaxErrorMessage.class.getDeclaredField("source");
                    sourceField.setAccessible(true);
                    SourceUnit sourceUnit = (SourceUnit) sourceField.get(syntaxErrorMessage);
                    Field nameField = SourceUnit.class.getDeclaredField("name");
                    nameField.setAccessible(true);
                    nameField.set(sourceUnit, source.getDisplayName());
                } catch (Exception failure) {
                    throw UncheckedException.throwAsUncheckedException(failure);
                }
            }
        }

        SyntaxException syntaxError = e.getErrorCollector().getSyntaxError(0);
        Integer lineNumber = syntaxError == null ? null : syntaxError.getLine();
        throw new ScriptCompilationException(String.format("Could not compile %s.", source.getDisplayName()), e, source, lineNumber);
    }

    private CompilerConfiguration createBaseCompilerConfiguration(Class<? extends Script> scriptBaseClass) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(scriptBaseClass.getName());
        return configuration;
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> loadFromDir(ScriptSource source, HashCode sourceHashCode, ClassLoaderScope targetScope, ClassPath scriptClassPath,
                                                                  File metadataCacheDir, CompileOperation<M> transformer, Class<T> scriptBaseClass) {
        File metadataFile = new File(metadataCacheDir, METADATA_FILE_NAME);
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(new FileInputStream(metadataFile))) {
            byte flags = decoder.readByte();
            boolean isEmpty = (flags & EMPTY_FLAG) != 0;
            boolean hasMethods = (flags & HAS_METHODS_FLAG) != 0;
            M data;
            if (transformer != null && transformer.getDataSerializer() != null) {
                data = transformer.getDataSerializer().read(decoder);
            } else {
                data = null;
            }
            return new ClassesDirCompiledScript<>(isEmpty, hasMethods, scriptBaseClass, scriptClassPath, targetScope, source, sourceHashCode, data);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Failed to deserialize script metadata extracted for %s", source.getDisplayName()), e);
        }
    }

    private static class PackageStatementDetector extends CompilationUnit.SourceUnitOperation {
        private boolean hasPackageStatement;

        @Override
        public void call(SourceUnit source) throws CompilationFailedException {
            hasPackageStatement = source.getAST().getPackageName() != null;
        }
    }

    private static class EmptyScriptDetector extends CompilationUnit.SourceUnitOperation {
        private boolean emptyScript;
        private boolean hasMethods;

        @Override
        public void call(SourceUnit source) {
            if (!source.getAST().getMethods().isEmpty()) {
                hasMethods = true;
            }
            emptyScript = isEmpty(source);
        }

        private boolean isEmpty(SourceUnit source) {
            List<Statement> statements = source.getAST().getStatementBlock().getStatements();
            for (Statement statement : statements) {
                if (AstUtils.mayHaveAnEffect(statement)) {
                    return false;
                }
            }

            // No statements, or no statements that have an effect
            return true;
        }

        public boolean getHasMethods() {
            return hasMethods;
        }

        public boolean isEmptyScript() {
            return emptyScript;
        }
    }

    private static class NoOpGroovyResourceLoader implements GroovyResourceLoader {
        @Override
        public URL loadGroovySource(String filename) {
            return null;
        }
    }

    private class CustomCompilationUnit extends CompilationUnit {
        public CustomCompilationUnit(CompilerConfiguration compilerConfiguration, CodeSource codeSource, final Action<? super ClassNode> customVerifier, GroovyClassLoader groovyClassLoader) {
            super(compilerConfiguration, codeSource, groovyClassLoader);
            this.verifier = new Verifier() {
                @Override
                public void visitClass(ClassNode node) {
                    customVerifier.execute(node);
                    super.visitClass(node);
                }
            };
            this.resolveVisitor = new GradleResolveVisitor(this, simpleNameToFQN);
        }
    }

    private static class ClassesDirCompiledScript<T extends Script, M> implements CompiledScript<T, M> {
        private final boolean isEmpty;
        private final boolean hasMethods;
        private final Class<T> scriptBaseClass;
        private final ClassPath scriptClassPath;
        private final ClassLoaderScope targetScope;
        private final ScriptSource source;
        private final HashCode sourceHashCode;
        private final M metadata;
        private Class<? extends T> scriptClass;
        private ClassLoaderScope scope;

        public ClassesDirCompiledScript(boolean isEmpty, boolean hasMethods, Class<T> scriptBaseClass, ClassPath scriptClassPath, ClassLoaderScope targetScope, ScriptSource source, HashCode sourceHashCode, M metadata) {
            this.isEmpty = isEmpty;
            this.hasMethods = hasMethods;
            this.scriptBaseClass = scriptBaseClass;
            this.scriptClassPath = scriptClassPath;
            this.targetScope = targetScope;
            this.source = source;
            this.sourceHashCode = sourceHashCode;
            this.metadata = metadata;
        }

        @Override
        public boolean getRunDoesSomething() {
            return !isEmpty;
        }

        @Override
        public boolean getHasMethods() {
            return hasMethods;
        }

        @Override
        public M getData() {
            return metadata;
        }

        @Override
        public void onReuse() {
            if (scriptClass != null) {
                // Recreate the script scope and ClassLoader, so that things that use scopes are notified that the scope exists
                scope.onReuse();
                assert scriptClass.getClassLoader() == scope.getLocalClassLoader();
            }
        }

        @Override
        public Class<? extends T> loadClass() {
            if (scriptClass == null) {
                if (isEmpty && !hasMethods) {
                    throw new UnsupportedOperationException("Cannot load script that does nothing.");
                }
                try {
                    scope = prepareClassLoaderScope();
                    ClassLoader loader = scope.getLocalClassLoader();
                    scriptClass = loader.loadClass(source.getClassName()).asSubclass(scriptBaseClass);
                } catch (Exception e) {
                    if (scriptClassPath.isEmpty()) {
                        throw new IllegalStateException(String.format("The cache entry for %s appears to be corrupted.", source.getDisplayName()));
                    }
                    throw new GradleException(String.format("Could not load compiled classes for %s from cache.", source.getDisplayName()), e);
                }
            }
            return scriptClass;
        }

        private ClassLoaderScope prepareClassLoaderScope() {
            String scopeName = "groovy-dsl:" + source.getFileName() + ":" + scriptBaseClass.getSimpleName();
            return targetScope.createLockedChild(scopeName, scriptClassPath, sourceHashCode, parent -> new ScriptClassLoader(source, parent, scriptClassPath, sourceHashCode));
        }
    }

    /**
     * A specialized ClassLoader that avoids unnecessary delegation to the parent ClassLoader, and the resulting cascade of ClassNotFoundExceptions for those classes that are known to be available only in this ClassLoader and nowhere else.
     */
    private static class ScriptClassLoader extends VisitableURLClassLoader implements ImplementationHashAware {
        private final ScriptSource scriptSource;
        private final HashCode implementationHash;

        ScriptClassLoader(ScriptSource scriptSource, ClassLoader parent, ClassPath classPath, HashCode implementationHash) {
            super("groovy-script-" + scriptSource.getFileName() + "-loader", parent, classPath);
            this.scriptSource = scriptSource;
            this.implementationHash = implementationHash;
        }

        @Override
        public HashCode getImplementationHash() {
            return implementationHash;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Generated script class name must be unique - take advantage of this to avoid delegation
            if (name.startsWith(scriptSource.getClassName())) {
                // Synchronized to avoid multiple threads attempting to define the same class on a lookup miss
                synchronized (this) {
                    Class<?> cl = findLoadedClass(name);
                    if (cl == null) {
                        cl = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(cl);
                    }
                    return cl;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
