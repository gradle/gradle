/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.publish.internal;

import com.google.common.collect.Collections2;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.MergeProvider;
import org.gradle.api.publish.PublicationArtifact;

public class DefaultPublicationArtifactSet<T extends PublicationArtifact> extends DefaultDomainObjectSet<T> implements PublicationArtifactSet<T> {

    private final FileCollection files;

    public DefaultPublicationArtifactSet(
        Class<T> type,
        FileCollectionFactory fileCollectionFactory,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator
    ) {
        super(type, collectionCallbackActionDecorator);
        this.files = fileCollectionFactory.fromProvider(MergeProvider.of(
            Collections2.transform(this, artifact -> ((PublicationArtifactInternal) artifact).getFileProvider())
        ));
    }

    @Override
    public FileCollection getFiles() {
        return files;
    }
}
