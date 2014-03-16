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

package org.gradle.plugins.ear

import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.MapFileTree
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.nativeplatform.filesystem.Chmod
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor
import org.gradle.plugins.ear.descriptor.EarModule
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor
import org.gradle.plugins.ear.descriptor.internal.DefaultEarModule
import org.gradle.plugins.ear.descriptor.internal.DefaultEarWebModule
import org.gradle.util.ConfigureUtil

/**
 * Assembles an EAR archive.
 */
class Ear extends Jar {
    public static final String EAR_EXTENSION = 'ear'

    /**
     * The name of the library directory in the EAR file. Default is "lib".
     */
    String libDirName

    /**
     * The deployment descriptor configuration.
     */
    DeploymentDescriptor deploymentDescriptor

    private CopySpec lib

    Ear() {
        extension = EAR_EXTENSION
        lib = rootSpec.addChildBeforeSpec(mainSpec).into {
            getLibDirName()
        }
        mainSpec.eachFile { FileCopyDetails details ->
            if (this.deploymentDescriptor && details.path.equalsIgnoreCase('META-INF/' + this.deploymentDescriptor.fileName)) {
                // the deployment descriptor already exists; no need to generate it
                this.deploymentDescriptor = null
            }
            // since we might generate the deployment descriptor, record each top-level module
            if (this.deploymentDescriptor && details.path.lastIndexOf('/') <= 0) {
                EarModule module
                if (details.path.toLowerCase().endsWith(".war")) {
                    module = new DefaultEarWebModule(details.path, details.path.substring(0, details.path.lastIndexOf('.')))
                } else {
                    module = new DefaultEarModule(details.path)
                }
                if (!this.deploymentDescriptor.modules.contains(module)) {
                    this.deploymentDescriptor.modules.add module
                }
            }
        }
        // create our own metaInf which runs after mainSpec's files
        // this allows us to generate the deployment descriptor after recording all modules it contains
        def metaInf = mainSpec.addChild().into('META-INF')
        metaInf.addChild().from {
            MapFileTree descriptorSource = new MapFileTree(temporaryDirFactory, services.get(Chmod))
            final DeploymentDescriptor descriptor = deploymentDescriptor
            if (descriptor) {
                if (!descriptor.libraryDirectory) {
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
    Ear deploymentDescriptor(Closure configureClosure) {
        if (!deploymentDescriptor) {
            deploymentDescriptor = new DefaultDeploymentDescriptor(project.fileResolver) // implied use of ProjectInternal
        }
        ConfigureUtil.configure(configureClosure, deploymentDescriptor)
        this
    }

    /**
     * A location for dependency libraries to include in the 'lib' directory of the EAR archive.
     */
    public CopySpec getLib() {
        return lib.addChild()
    }

    /**
     * Adds dependency libraries to include in the 'lib' directory of the EAR archive.
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
