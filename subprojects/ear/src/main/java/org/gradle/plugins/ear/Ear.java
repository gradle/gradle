/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ear;

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.GeneratedSingletonFileTree;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.EarModule;
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.internal.DefaultEarModule;
import org.gradle.plugins.ear.descriptor.internal.DefaultEarWebModule;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.concurrent.Callable;

import static org.gradle.plugins.ear.EarPlugin.DEFAULT_LIB_DIR_NAME;

/**
 * Assembles an EAR archive.
 */
public class Ear extends Jar {
    public static final String EAR_EXTENSION = "ear";

    private String libDirName;
    private final Property<Boolean> generateDeploymentDescriptor;
    private DeploymentDescriptor deploymentDescriptor;
    private CopySpec lib;

    public Ear() {
        getArchiveExtension().set(EAR_EXTENSION);
        setMetadataCharset("UTF-8");
        generateDeploymentDescriptor = getObjectFactory().property(Boolean.class);
        generateDeploymentDescriptor.convention(true);
        lib = getRootSpec().addChildBeforeSpec(getMainSpec()).into(
            (Callable<String>) () -> GUtil.elvis(getLibDirName(), DEFAULT_LIB_DIR_NAME)
        );
        getMainSpec().appendCachingSafeCopyAction(details -> {
                if(generateDeploymentDescriptor.get()) {
                    checkIfShouldGenerateDeploymentDescriptor(details);
                    recordTopLevelModules(details);
                }
            }
        );

        // create our own metaInf which runs after mainSpec's files
        // this allows us to generate the deployment descriptor after recording all modules it contains
        CopySpecInternal metaInf = (CopySpecInternal) getMainSpec().addChild().into("META-INF");
        CopySpecInternal descriptorChild = metaInf.addChild();
        OutputChangeListener outputChangeListener = getServices().get(OutputChangeListener.class);
        descriptorChild.from((Callable<FileTreeAdapter>) () -> {
                final DeploymentDescriptor descriptor = getDeploymentDescriptor();

                if (descriptor != null && generateDeploymentDescriptor.get()) {
                    if (descriptor.getLibraryDirectory() == null) {
                        descriptor.setLibraryDirectory(getLibDirName());
                    }

                String descriptorFileName = descriptor.getFileName();
                if (descriptorFileName.contains("/") || descriptorFileName.contains(File.separator)) {
                    throw new InvalidUserDataException("Deployment descriptor file name must be a simple name but was " + descriptorFileName);
                    }
                GeneratedSingletonFileTree descriptorSource = new GeneratedSingletonFileTree(
                    getTemporaryDirFactory(),
                    descriptorFileName,
                    absolutePath -> outputChangeListener.beforeOutputChange(ImmutableList.of(absolutePath)),
                    outputStream -> descriptor.writeTo(new OutputStreamWriter(outputStream))
                );


                    return new FileTreeAdapter(descriptorSource);
                }

                return null;
        });
    }

    private void recordTopLevelModules(FileCopyDetails details) {
        DeploymentDescriptor deploymentDescriptor = getDeploymentDescriptor();
        // since we might generate the deployment descriptor, record each top-level module
        if (deploymentDescriptor != null && details.getPath().lastIndexOf("/") <= 0) {
            EarModule module;
            if (details.getPath().toLowerCase().endsWith(".war")) {
                module = new DefaultEarWebModule(details.getPath(), details.getPath().substring(0, details.getPath().lastIndexOf(".")));
            } else {
                module = new DefaultEarModule(details.getPath());
            }

            deploymentDescriptor.getModules().add(module);
        }
    }

    private void checkIfShouldGenerateDeploymentDescriptor(FileCopyDetails details) {
        DeploymentDescriptor deploymentDescriptor = getDeploymentDescriptor();
        String descriptorPath = deploymentDescriptor != null ? "META-INF/" + deploymentDescriptor.getFileName() : null;
        if (details.getPath().equalsIgnoreCase(descriptorPath)) {
            // the deployment descriptor already exists; no need to generate it
            setDeploymentDescriptor(null);
            details.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        }
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given closure is executed to configure the deployment descriptor. The {@link DeploymentDescriptor} is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return This.
     */
    public Ear deploymentDescriptor(@DelegatesTo(value = DeploymentDescriptor.class, strategy = Closure.DELEGATE_FIRST) Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, forceDeploymentDescriptor());
        return this;
    }

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given action is executed to configure the deployment descriptor.</p>
     *
     * @param configureAction The action.
     * @return This.
     * @since 3.5
     */
    public Ear deploymentDescriptor(Action<? super DeploymentDescriptor> configureAction) {
        configureAction.execute(forceDeploymentDescriptor());
        return this;
    }

    private DeploymentDescriptor forceDeploymentDescriptor() {
        if (deploymentDescriptor == null) {
            deploymentDescriptor = getObjectFactory().newInstance(DefaultDeploymentDescriptor.class);
        }
        return deploymentDescriptor;
    }

    /**
     * A location for dependency libraries to include in the 'lib' directory of the EAR archive.
     */
    @Internal
    public CopySpec getLib() {
        return ((CopySpecInternal) lib).addChild();
    }

    /**
     * Adds dependency libraries to include in the 'lib' directory of the EAR archive.
     *
     * <p>The given closure is executed to configure a {@code CopySpec}. The {@link CopySpec} is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return The created {@code CopySpec}
     */
    public CopySpec lib(@DelegatesTo(value = CopySpec.class, strategy = Closure.DELEGATE_FIRST) Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getLib());
    }

    /**
     * Adds dependency libraries to include in the 'lib' directory of the EAR archive.
     *
     * <p>The given action is executed to configure a {@code CopySpec}.</p>
     *
     * @param configureAction The action.
     * @return The created {@code CopySpec}
     * @since 3.5
     */
    public CopySpec lib(Action<? super CopySpec> configureAction) {
        CopySpec copySpec = getLib();
        configureAction.execute(copySpec);
        return copySpec;
    }

    /**
     * The name of the library directory in the EAR file. Default is "lib".
     */
    @Nullable
    @Optional
    @Input
    public String getLibDirName() {
        return libDirName;
    }

    public void setLibDirName(@Nullable String libDirName) {
        this.libDirName = libDirName;
    }

    /**
     * Should deploymentDescriptor be generated?
     *
     * @since 6.0
     */
    @Input
    @Incubating
    public Property<Boolean> getGenerateDeploymentDescriptor() {
        return generateDeploymentDescriptor;
    }

    /**
     * The deployment descriptor configuration.
     */
    @Internal
    public DeploymentDescriptor getDeploymentDescriptor() {
        return deploymentDescriptor;
    }

    public void setDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor) {
        this.deploymentDescriptor = deploymentDescriptor;
    }

}
