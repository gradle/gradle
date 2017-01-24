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

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.MapFileTree;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.EarModule;
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.internal.DefaultEarModule;
import org.gradle.plugins.ear.descriptor.internal.DefaultEarWebModule;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.Callable;

import static org.gradle.plugins.ear.EarPlugin.DEFAULT_LIB_DIR_NAME;

/**
 * Assembles an EAR archive.
 */
@CacheableTask
public class Ear extends Jar {
    public static final String EAR_EXTENSION = "ear";

    private String libDirName;
    private DeploymentDescriptor deploymentDescriptor;
    private CopySpec lib;

    public Ear() {
        setExtension(EAR_EXTENSION);
        setMetadataCharset("UTF-8");
        lib = getRootSpec().addChildBeforeSpec(getMainSpec()).into(new Callable<String>() {
            public String call() {
                return GUtil.elvis(getLibDirName(), DEFAULT_LIB_DIR_NAME);
            }
        });
        getMainSpec().appendCachingSafeCopyAction(
            new Action<FileCopyDetails>() {
                @Override
                public void execute(FileCopyDetails details) {
                    checkIfShouldGenerateDeploymentDescriptor(details);
                    recordTopLevelModules(details);
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

                        if (!deploymentDescriptor.getModules().contains(module)) {
                            deploymentDescriptor.getModules().add(module);
                        }
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
            }
        );

        // create our own metaInf which runs after mainSpec's files
        // this allows us to generate the deployment descriptor after recording all modules it contains
        CopySpecInternal metaInf = (CopySpecInternal) getMainSpec().addChild().into("META-INF");
        metaInf.addChild().from(new Callable<FileTreeAdapter>() {
            public FileTreeAdapter call() {
                final DeploymentDescriptor descriptor = getDeploymentDescriptor();
                if (descriptor != null) {
                    MapFileTree descriptorSource = new MapFileTree(getTemporaryDirFactory(), getFileSystem());
                    if (descriptor.getLibraryDirectory() == null) {
                        descriptor.setLibraryDirectory(getLibDirName());
                    }

                    descriptorSource.add(descriptor.getFileName(), new Action<OutputStream>() {
                        public void execute(OutputStream outputStream) {
                            descriptor.writeTo(new OutputStreamWriter(outputStream));
                        }

                    });
                    return new FileTreeAdapter(descriptorSource);
                }

                return null;
            }
        });
    }

    @Inject
    protected Instantiator getInstantiator() {
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
        if (deploymentDescriptor == null) {
            deploymentDescriptor = getInstantiator().newInstance(DefaultDeploymentDescriptor.class, getFileResolver(), getInstantiator());
        }

        ConfigureUtil.configure(configureClosure, deploymentDescriptor);
        return this;
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
     * The name of the library directory in the EAR file. Default is "lib".
     */
    @Optional
    @Input
    public String getLibDirName() {
        return libDirName;
    }

    public void setLibDirName(String libDirName) {
        this.libDirName = libDirName;
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
