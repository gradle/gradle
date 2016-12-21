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
package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.ClosureBackedTransformer;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionExecuter;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.CopySpecResolver;
import org.gradle.api.internal.file.copy.CopySpecSource;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.specs.Spec;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.DeprecationLogger;

import javax.inject.Inject;
import java.io.FilterReader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * {@code AbstractCopyTask} is the base class for all copy tasks.
 */
public abstract class AbstractCopyTask extends ConventionTask implements CopySpec, CopySpecSource {

    private static final String CONFIGURE_SPEC_DURING_CONFIGURATION = "Consider configuring the spec during configuration time, or using a separate task to do the configuration";

    private final CopySpecInternal rootSpec;
    private final CopySpecInternal mainSpec;

    protected AbstractCopyTask() {
        this.rootSpec = createRootSpec();
        rootSpec.addChildSpecListener(new CopySpecInternal.CopySpecListener() {
            @Override
            public void childSpecAdded(CopySpecInternal.CopySpecAddress path, final CopySpecInternal spec) {
                if (getState().getExecuting()) {
                    if (getOutputs().isCacheEnabled() && getProject().getGradle().getStartParameter().isTaskOutputCacheEnabled()) {
                        throw new GradleException("It is not allowed to modify child specs of the task at execution time when task output caching is enabled. "
                            + CONFIGURE_SPEC_DURING_CONFIGURATION + ".");
                    }
                    DeprecationLogger.nagUserOfDeprecated(
                        "Configuring child specs of a copy task at execution time of the task",
                        CONFIGURE_SPEC_DURING_CONFIGURATION
                    );
                    return;
                }

                StringBuilder specPropertyNameBuilder = new StringBuilder("rootSpec");
                CopySpecResolver parentResolver = path.unroll(specPropertyNameBuilder);
                final CopySpecResolver resolver = spec.buildResolverRelativeToParent(parentResolver);
                String specPropertyName = specPropertyNameBuilder.toString();

                getInputs().files(new Callable<FileTree>() {
                    @Override
                    public FileTree call() throws Exception {
                        return resolver.getSource();
                    }
                })
                    .withPropertyName(specPropertyName)
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .skipWhenEmpty();

                getInputs().property(specPropertyName + ".destPath", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return resolver.getDestPath().getPathString();
                    }
                });
                getInputs().property(specPropertyName + ".caseSensitive", new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return spec.isCaseSensitive();
                    }
                });
                getInputs().property(specPropertyName + ".includeEmptyDirs", new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return spec.getIncludeEmptyDirs();
                    }
                });
                getInputs().property(specPropertyName + ".duplicatesStrategy", new Callable<DuplicatesStrategy>() {
                    @Override
                    public DuplicatesStrategy call() throws Exception {
                        return spec.getDuplicatesStrategy();
                    }
                });
                getInputs().property(specPropertyName + ".dirMode", new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return spec.getDirMode();
                    }
                });
                getInputs().property(specPropertyName + ".fileMode", new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return spec.getFileMode();
                    }
                });
                getInputs().property(specPropertyName + ".filteringCharset", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return spec.getFilteringCharset();
                    }
                });
            }
        });
        this.getOutputs().doNotCacheIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                return rootSpec.hasCustomActions();
            }
        });
        this.mainSpec = rootSpec.addChild();
    }

    protected CopySpecInternal createRootSpec() {
        Instantiator instantiator = getInstantiator();
        FileResolver fileResolver = getFileResolver();
        return instantiator.newInstance(DefaultCopySpec.class, fileResolver, instantiator);
    }

    protected abstract CopyAction createCopyAction();

    @Inject
    protected Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileSystem getFileSystem() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileLookup getFileLookup() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    protected void copy() {
        Instantiator instantiator = getInstantiator();
        FileSystem fileSystem = getFileSystem();

        CopyActionExecuter copyActionExecuter = new CopyActionExecuter(instantiator, fileSystem);
        CopyAction copyAction = createCopyAction();
        WorkResult didWork = copyActionExecuter.execute(rootSpec, copyAction);
        setDidWork(didWork.getDidWork());
    }

    /**
     * Returns the source files for this task.
     * @return The source files. Never returns null.
     */
    @Internal
    public FileCollection getSource() {
        return rootSpec.buildRootResolver().getAllSource();
    }

    @Internal
    public CopySpecInternal getRootSpec() {
        return rootSpec;
    }

    // -----------------------------------------------
    // ---- Delegate CopySpec methods to rootSpec ----
    // -----------------------------------------------

    @Internal
    protected CopySpecInternal getMainSpec() {
        return mainSpec;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public boolean isCaseSensitive() {
        return getMainSpec().isCaseSensitive();
    }

    /**
     * {@inheritDoc}
     */
    public void setCaseSensitive(boolean caseSensitive) {
        getMainSpec().setCaseSensitive(caseSensitive);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public boolean getIncludeEmptyDirs() {
        return getMainSpec().getIncludeEmptyDirs();
    }

    /**
     * {@inheritDoc}
     */
    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        getMainSpec().setIncludeEmptyDirs(includeEmptyDirs);
    }

    /**
     * {@inheritDoc}
     */
    public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
        getRootSpec().setDuplicatesStrategy(strategy);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public DuplicatesStrategy getDuplicatesStrategy() {
        return getRootSpec().getDuplicatesStrategy();
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask from(Object... sourcePaths) {
        getMainSpec().from(sourcePaths);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filesMatching(String pattern, Action<? super FileCopyDetails> action) {
        getMainSpec().filesMatching(pattern, action);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filesMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action) {
        getMainSpec().filesMatching(patterns, action);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filesNotMatching(String pattern, Action<? super FileCopyDetails> action) {
        getMainSpec().filesNotMatching(pattern, action);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filesNotMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action) {
        getMainSpec().filesNotMatching(patterns, action);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask from(Object sourcePath, final Closure c) {
        getMainSpec().from(sourcePath, new ClosureBackedAction<CopySpec>(c));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask from(Object sourcePath, Action<? super CopySpec> configureAction) {
        getMainSpec().from(sourcePath, configureAction);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec with(CopySpec... sourceSpecs) {
        getMainSpec().with(sourceSpecs);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask into(Object destDir) {
        getRootSpec().into(destDir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask into(Object destPath, Closure configureClosure) {
        getMainSpec().into(destPath, configureClosure);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec into(Object destPath, Action<? super CopySpec> copySpec) {
        getMainSpec().into(destPath, copySpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask include(String... includes) {
        getMainSpec().include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask include(Iterable<String> includes) {
        getMainSpec().include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask include(Spec<FileTreeElement> includeSpec) {
        getMainSpec().include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask include(Closure includeSpec) {
        getMainSpec().include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask exclude(String... excludes) {
        getMainSpec().exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask exclude(Iterable<String> excludes) {
        getMainSpec().exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask exclude(Spec<FileTreeElement> excludeSpec) {
        getMainSpec().exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask exclude(Closure excludeSpec) {
        getMainSpec().exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask setIncludes(Iterable<String> includes) {
        getMainSpec().setIncludes(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public Set<String> getIncludes() {
        return getMainSpec().getIncludes();
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask setExcludes(Iterable<String> excludes) {
        getMainSpec().setExcludes(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public Set<String> getExcludes() {
        return getMainSpec().getExcludes();
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask rename(final Closure closure) {
        return rename(new ClosureBackedTransformer(closure));
    }

    /**
     * {@inheritDoc}
     * @param renamer
     */
    public AbstractCopyTask rename(Transformer<String, String> renamer) {
        getMainSpec().rename(renamer);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask rename(String sourceRegEx, String replaceWith) {
        getMainSpec().rename(sourceRegEx, replaceWith);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask rename(Pattern sourceRegEx, String replaceWith) {
        getMainSpec().rename(sourceRegEx, replaceWith);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        getMainSpec().filter(properties, filterType);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filter(Class<? extends FilterReader> filterType) {
        getMainSpec().filter(filterType);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filter(Closure closure) {
        getMainSpec().filter(closure);
        return this;
    }

    /**
     * {@inheritDoc}
     * @param transformer
     */
    public AbstractCopyTask filter(Transformer<String, String> transformer) {
        getMainSpec().filter(transformer);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask expand(Map<String, ?> properties) {
        getMainSpec().expand(properties);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public Integer getDirMode() {
        return getMainSpec().getDirMode();
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public Integer getFileMode() {
        return getMainSpec().getFileMode();
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask setDirMode(Integer mode) {
        getMainSpec().setDirMode(mode);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask setFileMode(Integer mode) {
        getMainSpec().setFileMode(mode);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask eachFile(Action<? super FileCopyDetails> action) {
        getMainSpec().eachFile(action);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask eachFile(Closure closure) {
        getMainSpec().eachFile(closure);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    public String getFilteringCharset() {
        return getMainSpec().getFilteringCharset();
    }

    /**
     * {@inheritDoc}
     */
    public void setFilteringCharset(String charset) {
        getMainSpec().setFilteringCharset(charset);
    }
}
