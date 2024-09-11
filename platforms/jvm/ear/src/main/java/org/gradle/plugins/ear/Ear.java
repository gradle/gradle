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

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.EarModule;
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.internal.DefaultEarModule;
import org.gradle.plugins.ear.descriptor.internal.DefaultEarWebModule;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singleton;
import static org.gradle.api.internal.lambdas.SerializableLambdas.action;
import static org.gradle.api.internal.lambdas.SerializableLambdas.callable;
import static org.gradle.plugins.ear.EarPlugin.DEFAULT_LIB_DIR_NAME;
import static org.gradle.plugins.ear.descriptor.internal.DeploymentDescriptorXmlWriter.writeDeploymentDescriptor;

/**
 * Assembles an EAR archive.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class Ear extends Jar {
    public static final String EAR_EXTENSION = "ear";

    private final Property<Boolean> generateDeploymentDescriptor;
    private DeploymentDescriptor deploymentDescriptor;
    private final CopySpec lib;
    private final DirectoryProperty appDir;

    public Ear() {
        getArchiveExtension().set(EAR_EXTENSION);
        setMetadataCharset("UTF-8");
        generateDeploymentDescriptor = getObjectFactory().property(Boolean.class);
        generateDeploymentDescriptor.convention(true);
        lib = getRootSpec().addChildBeforeSpec(getMainSpec()).into(getLibDirName().orElse(DEFAULT_LIB_DIR_NAME));
        AtomicReference<Set<EarModule>> topLevelModules = new AtomicReference<>(new LinkedHashSet<>());
        getMainSpec().appendCachingSafeCopyAction(action(details -> {
            if (generateDeploymentDescriptor.get()) {
                checkIfShouldGenerateDeploymentDescriptor(details);
                topLevelModules.set(Sets.union(topLevelModules.get(), recordTopLevelModules(details)));
            }
        }));

        // create our own metaInf which runs after mainSpec's files
        // this allows us to generate the deployment descriptor after recording all modules it contains
        CopySpecInternal metaInf = (CopySpecInternal) getMainSpec().addChild().into("META-INF");
        CopySpecInternal descriptorChild = metaInf.addChild();
        // the generated descriptor should only be used if one does not already exist
        descriptorChild.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        descriptorChild.from(callable(() -> {
            final DeploymentDescriptor descriptor = getDeploymentDescriptor();
            if (descriptor != null && generateDeploymentDescriptor.get()) {
                String descriptorFileName = descriptor.getFileName();
                if (descriptorFileName.contains("/") || descriptorFileName.contains(File.separator)) {
                    throw new InvalidUserDataException("Deployment descriptor file name must be a simple name but was " + descriptorFileName);
                }
                descriptor.getLibraryDirectory().convention(getLibDirName());

                // TODO: Consider capturing the `descriptor` as a spec
                //  so any captured manifest attribute providers are re-evaluated
                //  on each run.
                //  See https://github.com/gradle/configuration-cache/issues/168
                final OutputChangeListener outputChangeListener = outputChangeListener();
                return fileCollectionFactory().generated(
                    getTemporaryDirFactory(),
                    descriptorFileName,
                    action(file -> outputChangeListener.invalidateCachesFor(singleton(file.getAbsolutePath()))),
                    action(outputStream -> {
                            // delay obtaining contents to account for descriptor changes
                            // (for instance, due to modules discovered)
                            Set<EarModule> modules = Sets.union(descriptor.getModules().get(), topLevelModules.get());
                            writeDeploymentDescriptor(descriptor, modules, new XmlTransformer(), new OutputStreamWriter(outputStream));
                        }
                    )
                );
            }

            return null;
        }));

        appDir = getObjectFactory().directoryProperty();
    }

    private FileCollectionFactory fileCollectionFactory() {
        return getServices().get(FileCollectionFactory.class);
    }

    private OutputChangeListener outputChangeListener() {
        return getServices().get(OutputChangeListener.class);
    }

    private Set<EarModule> recordTopLevelModules(FileCopyDetails details) {
        Set<EarModule> topLevelModules = new LinkedHashSet<>();
        DeploymentDescriptor deploymentDescriptor = getDeploymentDescriptor();
        // since we might generate the deployment descriptor, record each top-level module
        if (deploymentDescriptor != null && details.getPath().lastIndexOf("/") <= 0) {
            EarModule module;
            if (details.getPath().toLowerCase(Locale.ROOT).endsWith(".war")) {
                module = getObjectFactory().newInstance(DefaultEarWebModule.class);
                ((DefaultEarWebModule) module).getContextRoot().set(details.getPath().substring(0, details.getPath().lastIndexOf(".")));
                module.getPath().set(details.getPath());
            } else {
                module = getObjectFactory().newInstance(DefaultEarModule.class);
                module.getPath().set(details.getPath());
            }

            topLevelModules.add(module);
        }
        return topLevelModules;
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
    @Override
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
    @NotToBeReplacedByLazyProperty(because = "Read-only CopySpec property")
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
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getLibDirName();

    /**
     * Should deploymentDescriptor be generated?
     *
     * @since 6.0
     */
    @Input
    public Property<Boolean> getGenerateDeploymentDescriptor() {
        return generateDeploymentDescriptor;
    }

    /**
     * The deployment descriptor configuration.
     */
    @Internal
    @NotToBeReplacedByLazyProperty(because = "Nested like property")
    public DeploymentDescriptor getDeploymentDescriptor() {
        return deploymentDescriptor;
    }

    public void setDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor) {
        this.deploymentDescriptor = deploymentDescriptor;
    }

    /**
     * The application directory. Added to the produced archive by default.
     * <p>
     * The {@code ear} plugin sets the default value for all {@code Ear} tasks to {@code src/main/application}.
     * <p>
     * Note, that if the {@code ear} plugin is not applied then this property is ignored.
     *
     * @since 7.1
     */
    @Internal
    public DirectoryProperty getAppDirectory() {
        return appDir;
    }
}
