/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.cache.internal.MapBackedCache;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.internal.GFileUtils;
import sbt.internal.inc.Analysis;
import sbt.internal.inc.IncrementalCompilerImpl;
import sbt.internal.inc.Locate;
import sbt.internal.inc.LoggedReporter;
import sbt.internal.inc.PlainVirtualFileConverter;
import sbt.internal.inc.ScalaInstance;
import scala.Option;
import xsbti.T2;
import xsbti.VirtualFile;
import xsbti.compile.AnalysisContents;
import xsbti.compile.AnalysisStore;
import xsbti.compile.ClassFileManagerType;
import xsbti.compile.ClasspathOptionsUtil;
import xsbti.compile.CompileAnalysis;
import xsbti.compile.CompileOptions;
import xsbti.compile.CompileResult;
import xsbti.compile.CompilerCache;
import xsbti.compile.Compilers;
import xsbti.compile.DefinesClass;
import xsbti.compile.IncOptions;
import xsbti.compile.Inputs;
import xsbti.compile.PerClasspathEntryLookup;
import xsbti.compile.PreviousResult;
import xsbti.compile.ScalaCompiler;
import xsbti.compile.Setup;
import xsbti.compile.TransactionalManagerType;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ZincScalaCompiler implements Compiler<ScalaJavaJointCompileSpec> {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompiler.class);

    private final ScalaInstance scalaInstance;
    private final ScalaCompiler scalaCompiler;
    private final AnalysisStoreProvider analysisStoreProvider;
    private final static PlainVirtualFileConverter CONVERTER = PlainVirtualFileConverter.converter();

    private final ClearableMapBackedCache<VirtualFile, DefinesClass> definesClassCache = new ClearableMapBackedCache<>(new ConcurrentHashMap<>());

    @Inject
    public ZincScalaCompiler(ScalaInstance scalaInstance, ScalaCompiler scalaCompiler, AnalysisStoreProvider analysisStoreProvider) {
        this.scalaInstance = scalaInstance;
        this.scalaCompiler = scalaCompiler;
        this.analysisStoreProvider = analysisStoreProvider;
    }

    public WorkResult execute(final ScalaJavaJointCompileSpec spec) {

        LOGGER.info("Compiling with Zinc Scala compiler.");

        Timer timer = Time.startTimer();

        IncrementalCompilerImpl incremental = new IncrementalCompilerImpl();

        Compilers compilers = incremental.compilers(scalaInstance, ClasspathOptionsUtil.boot(), Option.apply(Jvm.current().getJavaHome().toPath()), scalaCompiler);

        List<String> scalacOptions = new ZincScalaCompilerArgumentsGenerator().generate(spec);
        List<String> javacOptions = new JavaCompilerArgumentsBuilder(spec).includeClasspath(false).noEmptySourcePath().build();

        List<VirtualFile> classpath = new LinkedList<>();
        for (File classpathEntry : spec.getCompileClasspath()){
            classpath.add(CONVERTER.toVirtualFile(classpathEntry.toPath()));
        }
        List<VirtualFile> sourceFiles = new LinkedList<>();
        for(File f: spec.getSourceFiles()){
            sourceFiles.add(CONVERTER.toVirtualFile(f.toPath()));
        }
        CompileOptions compileOptions = CompileOptions.create()
                .withSources(Iterables.toArray(sourceFiles, VirtualFile.class))
                .withClasspath(Iterables.toArray(classpath, VirtualFile.class))
                .withScalacOptions(scalacOptions.toArray(new String[0]))
                .withClassesDirectory(spec.getDestinationDir().toPath())
                .withJavacOptions(javacOptions.toArray(new String[0]));

        File analysisFile = spec.getAnalysisFile();
        Optional<AnalysisStore> analysisStore;
        Optional<ClassFileManagerType> classFileManagerType;
        if (spec.getScalaCompileOptions().isForce()) {
            analysisStore = Optional.empty();
            classFileManagerType = IncOptions.defaultClassFileManagerType();
        } else {
            analysisStore = Optional.of(analysisStoreProvider.get(analysisFile));
            classFileManagerType = Optional.of(TransactionalManagerType.of(spec.getClassfileBackupDir(), new SbtLoggerAdapter()));
        }

        PreviousResult previousResult = analysisStore.flatMap(store -> store.get()
            .map(a -> PreviousResult.of(Optional.of(a.getAnalysis()), Optional.of(a.getMiniSetup()))))
            .orElse(PreviousResult.of(Optional.empty(), Optional.empty()));

        IncOptions incOptions = IncOptions.of()
                .withRecompileOnMacroDef(Optional.of(false))
                .withClassfileManagerType(classFileManagerType)
                .withTransitiveStep(5);

        Setup setup = incremental.setup(new EntryLookup(spec),
                false,
                analysisFile.toPath(),
                CompilerCache.fresh(),
                incOptions,
                // MappedPosition is used to make sure toString returns proper error messages
                new LoggedReporter(100, new SbtLoggerAdapter(), MappedPosition::new),
                Option.empty(),
                Option.empty(),
                getExtra()
        );

        Inputs inputs = incremental.inputs(compileOptions, compilers, setup, previousResult);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(inputs.toString());
        }
        if (spec.getScalaCompileOptions().isForce()) {
            // TODO This should use Deleter
            GFileUtils.deleteDirectory(spec.getDestinationDir());
            GFileUtils.deleteQuietly(spec.getAnalysisFile());
        }
        LOGGER.info("Prepared Zinc Scala inputs: {}", timer.getElapsed());

        try {
            CompileResult compile = incremental.compile(inputs, new SbtLoggerAdapter());
            if (analysisStore.isPresent()) {
                AnalysisContents contentNext = AnalysisContents.create(compile.analysis(), compile.setup());
                analysisStore.get().set(contentNext);
            }
        } catch (xsbti.CompileFailed e) {
            throw new CompilationFailedException(e);
        }
        LOGGER.info("Completed Scala compilation: {}", timer.getElapsed());
        return WorkResults.didWork(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static T2<String, String>[] getExtra() {
        return new T2[0];
    }

    private class EntryLookup implements PerClasspathEntryLookup {
        private final Map<VirtualFile, File> analysisMap;

        public EntryLookup(ScalaJavaJointCompileSpec spec) {
            this.analysisMap = new HashMap<>();
            analysisMap.put(CONVERTER.toVirtualFile(spec.getDestinationDir().toPath()), spec.getAnalysisFile());
            for (Map.Entry<File, File> e: spec.getAnalysisMap().entrySet()){
                analysisMap.put(CONVERTER.toVirtualFile(e.getKey().toPath()), e.getValue());
            }
        }
        @Override
        public Optional<CompileAnalysis> analysis(VirtualFile classpathEntry) {
            return Optional.ofNullable(analysisMap.get(classpathEntry)).flatMap(f -> analysisStoreProvider.get(f).get().map(AnalysisContents::getAnalysis));
        }

        @Override
        public DefinesClass definesClass(VirtualFile classpathEntry) {
            if (classpathEntry.name().equals("rt.jar")) {
                return className -> false;
            }
            return analysis(classpathEntry)
                .map(a -> a instanceof Analysis ? (Analysis) a : null)
                .<DefinesClass>map(AnalysisBakedDefineClass::new)
                .orElseGet(() -> definesClassCache.get(classpathEntry, Locate::definesClass));
        }
    }

    private static class AnalysisBakedDefineClass implements DefinesClass {
        private final Analysis analysis;

        public AnalysisBakedDefineClass(Analysis analysis) {
            this.analysis = analysis;
        }

        @Override
        public boolean apply(String className) {
            return analysis.relations().productClassName().reverse(className).nonEmpty();
        }
    }

    private static class ClearableMapBackedCache<K, V> extends MapBackedCache<K, V> {

        private final Map<K, V> map;

        public ClearableMapBackedCache(Map<K, V> map) {
            super(map);
            this.map = map;
        }

        public void clear() {
            map.clear();
        }
    }
}
