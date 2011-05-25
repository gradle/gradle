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

package org.gradle.api.tasks.bundling

import org.gradle.api.enterprise.archives.DeploymentDescriptor
import org.gradle.api.enterprise.archives.EarModule
import org.gradle.api.enterprise.archives.internal.DefaultDeploymentDescriptor
import org.gradle.api.enterprise.archives.internal.DefaultEarModule
import org.gradle.api.enterprise.archives.internal.DefaultEarWebModule
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.MapFileTree
import org.gradle.util.ConfigureUtil

/**
 * Assembles an EAR archive.
 *
 * @author David Gileadi
 */
class Ear extends Jar {
    public static final String EAR_EXTENSION = 'ear'
    public static final String DEFAULT_LIB_DIR_NAME = 'lib'

    /**
     * The name for the library directory in the generated EAR. Defaults to "lib".
     */
    String libDirName
    /**
     * The deployment descriptor for this JAR archive, e.g. application.xml.
     */
    DeploymentDescriptor deploymentDescriptor

    private CopySpec lib

    Ear() {
        extension = EAR_EXTENSION
        lib = copyAction.rootSpec.addChild().into { libDirName ?: DEFAULT_LIB_DIR_NAME }
        copyAction.mainSpec.eachFile { FileCopyDetails details ->
            DeploymentDescriptor descriptor = deploymentDescriptor
            if (deploymentDescriptor && details.path.equalsIgnoreCase('META-INF/' + deploymentDescriptor.fileName)) {
                // the deployment descriptor already exists; no need to generate it
                deploymentDescriptor = null
            }
            // since we might generate the deployment descriptor, record each top-level module
            if (deploymentDescriptor && details.path.lastIndexOf('/') <= 0) {
                EarModule module
                if (details.path.toLowerCase().endsWith(".war")) {
                    module = new DefaultEarWebModule(details.path, details.path.substring(0, details.path.lastIndexOf('.')))
                } else {
                    module = new DefaultEarModule(details.path)
                }
                if (!deploymentDescriptor.modules.contains(module)) {
                    deploymentDescriptor.modules.add module
                }
            }
        }
        // create our own metaInf which runs after mainSpec's files
        // this allows us to generate the deployment descriptor after recording all modules it contains
        def metaInf = copyAction.mainSpec.addChild().into('META-INF')
        metaInf.addChild().from {
            MapFileTree descriptorSource = new MapFileTree(temporaryDir)
            final DeploymentDescriptor descriptor = deploymentDescriptor
            if (descriptor) {
                if (libDirName && !descriptor.libraryDirectory && libDirName != DEFAULT_LIB_DIR_NAME) {
                    descriptor.libraryDirectory = libDirName
                }
                descriptorSource.add(descriptor.fileName) {OutputStream outstr ->
                    descriptor.writeTo(new OutputStreamWriter(outstr))
                }
                return new FileTreeAdapter(descriptorSource)
            }
            return null
        }
    }

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given closure is executed to configure the deployment descriptor. The {@link DeploymentDescriptor}
     * is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return This.
     */
    public Ear deploymentDescriptor(Closure configureClosure) {
        if (!deploymentDescriptor) {
            deploymentDescriptor = new DefaultDeploymentDescriptor(project.fileResolver)
        }
        ConfigureUtil.configure(configureClosure, deploymentDescriptor)
        return this
    }

    /**
     * A location for dependency libraries to include in the 'lib' directory of the EAR archive (or whatever name is
     * configured via {@link #libDirName}).
     */
    public CopySpec getLib() {
        return lib.addChild()
    }

    /**
     * Adds dependency libraries to include in the 'lib' directory of the EAR archive (or whatever name is configured
     * via {@link #getLibDirName()}).
     *
     * <p>The given closure is executed to configure a {@code CopySpec}. The {@link CopySpec} is passed to the closure
     * as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return The created {@code CopySpec}
     */
    public CopySpec lib(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getLib())
    }
}
