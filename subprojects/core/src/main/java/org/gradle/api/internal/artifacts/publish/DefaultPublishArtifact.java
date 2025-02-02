/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.artifacts.publish;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;

import java.io.File;
import java.util.Date;

/**
 * This is only used for testing. Prefer:
 * <ui>
 *     <li>{@link org.gradle.api.internal.artifacts.dsl.FileSystemPublishArtifact} for fixed files</li>
 *     <li>{@link org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact} for providers of unknown type</li>
 *     <li>{@link org.gradle.api.internal.artifacts.publish.AbstractProviderBackedPublishArtifact} for providers of known types</li>
 * </ui>
 *
 * If necessary, any of the above implementations can be wrapped in
 * {@link DecoratingPublishArtifact} to provide mutability.
 */
@VisibleForTesting
public class DefaultPublishArtifact extends AbstractConfigurablePublishArtifact {

    private String name;
    private String extension;
    private String type;
    private String classifier;
    private Date date;
    private File file;

    public DefaultPublishArtifact(
        String name,
        String extension,
        String type,
        String classifier,
        Date date,
        File file,
        Object... tasks
    ) {
        super(DefaultTaskDependencyFactory.withNoAssociatedProject(), tasks);
        this.name = name;
        this.extension = extension;
        this.type = type;
        this.date = date;
        this.classifier = classifier;
        this.file = file;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public Date getDate() {
        return date;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setExtension(String extension) {
        this.extension = extension;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public boolean shouldBePublished() {
        return true;
    }
}
