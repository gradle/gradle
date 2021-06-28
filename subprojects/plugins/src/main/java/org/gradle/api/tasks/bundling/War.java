/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.tasks.bundling;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.internal.file.copy.RenamingCopyAction;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.Transformers;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * Assembles a WAR archive.
 */
@DisableCachingByDefault(because = "Not worth caching")
public class War extends Jar {
    public static final String WAR_EXTENSION = "war";

    private File webXml;
    private FileCollection classpath;
    private final DefaultCopySpec webInf;
    private final DirectoryProperty webAppDirectory;

    public War() {
        getArchiveExtension().set(WAR_EXTENSION);
        setMetadataCharset("UTF-8");
        // Add these as separate specs, so they are not affected by the changes to the main spec

        webInf = (DefaultCopySpec) getRootSpec().addChildBeforeSpec(getMainSpec()).into("WEB-INF");
        webInf.into("classes", spec -> spec.from((Callable<Iterable<File>>) () -> {
            FileCollection classpath = getClasspath();
            return classpath != null ? classpath.filter(spec(File::isDirectory)) : Collections.<File>emptyList();
        }));
        webInf.into("lib", spec -> spec.from((Callable<Iterable<File>>) () -> {
            FileCollection classpath = getClasspath();
            return classpath != null ? classpath.filter(spec(File::isFile)) : Collections.<File>emptyList();
        }));

        CopySpecInternal renameSpec = webInf.addChild();
        renameSpec.into("");
        renameSpec.from((Callable<File>) War.this::getWebXml);
        renameSpec.appendCachingSafeCopyAction(new RenamingCopyAction(Transformers.constant("web.xml")));

        webAppDirectory = getObjectFactory().directoryProperty();
    }

    @Inject
    public ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Internal
    public CopySpec getWebInf() {
        return webInf.addChild();
    }

    /**
     * Adds some content to the {@code WEB-INF} directory for this WAR archive.
     *
     * <p>The given closure is executed to configure a {@link CopySpec}. The {@code CopySpec} is passed to the closure as its delegate.
     *
     * @param configureClosure The closure to execute
     * @return The newly created {@code CopySpec}.
     */
    public CopySpec webInf(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getWebInf());
    }

    /**
     * Adds some content to the {@code WEB-INF} directory for this WAR archive.
     *
     * <p>The given action is executed to configure a {@link CopySpec}.
     *
     * @param configureAction The action to execute
     * @return The newly created {@code CopySpec}.
     * @since 3.5
     */
    public CopySpec webInf(Action<? super CopySpec> configureAction) {
        CopySpec webInf = getWebInf();
        configureAction.execute(webInf);
        return webInf;
    }

    /**
     * Returns the classpath to include in the WAR archive. Any JAR or ZIP files in this classpath are included in the {@code WEB-INF/lib} directory. Any directories in this classpath are included in
     * the {@code WEB-INF/classes} directory.
     *
     * @return The classpath. Returns an empty collection when there is no classpath to include in the WAR.
     */
    @Nullable
    @Optional
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath to include in the WAR archive.
     *
     * @param classpath The classpath. Must not be null.
     * @since 4.0
     */
    public void setClasspath(FileCollection classpath) {
        setClasspath((Object) classpath);
    }

    /**
     * Sets the classpath to include in the WAR archive.
     *
     * @param classpath The classpath. Must not be null.
     */
    public void setClasspath(Object classpath) {
        this.classpath = getProject().files(classpath);
    }

    /**
     * Adds files to the classpath to include in the WAR archive.
     *
     * @param classpath The files to add. These are evaluated as per {@link org.gradle.api.Project#files(Object...)}
     */
    public void classpath(Object... classpath) {
        FileCollection oldClasspath = getClasspath();
        this.classpath = getProject().files(oldClasspath != null ? oldClasspath : new ArrayList(), classpath);
    }

    /**
     * Returns the {@code web.xml} file to include in the WAR archive. When {@code null}, no {@code web.xml} file is included in the WAR.
     *
     * @return The {@code web.xml} file.
     */
    @Nullable
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public File getWebXml() {
        return webXml;
    }

    /**
     * Sets the {@code web.xml} file to include in the WAR archive. When {@code null}, no {@code web.xml} file is included in the WAR.
     *
     * @param webXml The {@code web.xml} file. Maybe null.
     */
    public void setWebXml(@Nullable File webXml) {
        this.webXml = webXml;
    }

    /**
     * Returns the app directory of the task. Added to the output web archive by default.
     * <p>
     * The {@code war} plugin sets the default value for all {@code War} tasks to {@code src/main/webapp} and adds it as a task input.
     * <p>
     * Note, that if the {@code war} plugin is not applied then this property is ignored. In that case, clients can manually set an app directory as a task input.
     *
     * @return The app directory.
     * @since 7.1
     */
    @Incubating
    @Internal
    public DirectoryProperty getWebAppDirectory() {
        return webAppDirectory;
    }
}
