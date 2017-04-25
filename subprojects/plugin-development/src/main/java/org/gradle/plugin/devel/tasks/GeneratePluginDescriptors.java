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

package org.gradle.plugin.devel.tasks;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Incubating;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.plugin.devel.PluginDeclaration;
import org.gradle.util.GUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;

/**
 * Generates plugin descriptors from plugin declarations.
 */
@Incubating
public class GeneratePluginDescriptors extends ConventionTask {

    @Input
    private List<PluginDeclaration> declarations = Lists.newArrayList();

    private Object outputDirectory;

    @TaskAction
    public void generatePluginDescriptors() {
        clearOutputDirectory();
        for (PluginDeclaration declaration : getDeclarations()) {
            File descriptorFile = new File(getOutputDirectory(), declaration.getId() + ".properties");
            Properties properties = new Properties();
            properties.setProperty("implementation-class", declaration.getImplementationClass());
            writePropertiesTo(properties, descriptorFile);
        }
    }

    private void clearOutputDirectory() {
        try {
            FileUtils.cleanDirectory(getOutputDirectory());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writePropertiesTo(Properties properties, File descriptorFile) {
        try {
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(descriptorFile));
            GUtil.savePropertiesNoDateComment(properties, outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<PluginDeclaration> getDeclarations() {
        return declarations;
    }

    public void setDeclarations(List<PluginDeclaration> declarations) {
        this.declarations = declarations;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        if (outputDirectory == null) {
            return null;
        }
        return getProject().file(outputDirectory);
    }

    /**
     * Sets the output directory.
     *
     * @since 4.0
     */
    public void setOutputDirectory(File outputDirectory) {
        setOutputDirectory((Object) outputDirectory);
    }

    public void setOutputDirectory(Object outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

}
