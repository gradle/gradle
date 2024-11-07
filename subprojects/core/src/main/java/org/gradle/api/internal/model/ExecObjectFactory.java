/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.model;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Similar as {@link ObjectFactory}, but with only methods that are accessible for Exec.
 */
@ServiceScope({Global.class})
public interface ExecObjectFactory {

    ConfigurableFileCollection fileCollection();

    ConfigurableFileTree fileTree();

    <T> Property<T> property(Class<T> valueType);

    <T> ListProperty<T> listProperty(Class<T> elementType);

    <T> SetProperty<T> setProperty(Class<T> elementType);

    <K, V> MapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType);

    DirectoryProperty directoryProperty();

    RegularFileProperty fileProperty();

    <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException;

    class DefaultExecObjectFactory implements ExecObjectFactory {
        private final Instantiator instantiator;
        private final PropertyFactory propertyFactory;
        private final FilePropertyFactory filePropertyFactory;
        private final FileCollectionFactory fileCollectionFactory;

        public DefaultExecObjectFactory(
            Instantiator instantiator,
            FileCollectionFactory fileCollectionFactory,
            PropertyFactory propertyFactory,
            FilePropertyFactory filePropertyFactory
        ) {
            this.instantiator = instantiator;
            this.fileCollectionFactory = fileCollectionFactory;
            this.propertyFactory = propertyFactory;
            this.filePropertyFactory = filePropertyFactory;
        }

        @Override
        public ConfigurableFileCollection fileCollection() {
            return fileCollectionFactory.configurableFiles();
        }

        @Override
        public ConfigurableFileTree fileTree() {
            return fileCollectionFactory.fileTree();
        }

        @Override
        public <T> Property<T> property(Class<T> valueType) {
            return propertyFactory.property(valueType);
        }

        @Override
        public <T> ListProperty<T> listProperty(Class<T> elementType) {
            return propertyFactory.listProperty(elementType);
        }

        @Override
        public <T> SetProperty<T> setProperty(Class<T> elementType) {
            return propertyFactory.setProperty(elementType);
        }

        @Override
        public <K, V> MapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType) {
            return propertyFactory.mapProperty(keyType, valueType);
        }

        @Override
        public DirectoryProperty directoryProperty() {
            return filePropertyFactory.newDirectoryProperty();
        }

        @Override
        public RegularFileProperty fileProperty() {
            return filePropertyFactory.newFileProperty();
        }

        @Override
        public <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException {
            return instantiator.newInstance(type, parameters);
        }
    }

    class ObjectFactoryBackedExecObjectFactory implements ExecObjectFactory {

        private final ObjectFactory objectFactory;

        public ObjectFactoryBackedExecObjectFactory(ObjectFactory objectFactory) {
            this.objectFactory = objectFactory;
        }

        @Override
        public ConfigurableFileCollection fileCollection() {
            return objectFactory.fileCollection();
        }

        @Override
        public ConfigurableFileTree fileTree() {
            return objectFactory.fileTree();
        }

        @Override
        public <T> Property<T> property(Class<T> valueType) {
            return objectFactory.property(valueType);
        }

        @Override
        public <T> ListProperty<T> listProperty(Class<T> elementType) {
            return objectFactory.listProperty(elementType);
        }

        @Override
        public <T> SetProperty<T> setProperty(Class<T> elementType) {
            return objectFactory.setProperty(elementType);
        }

        @Override
        public <K, V> MapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType) {
            return objectFactory.mapProperty(keyType, valueType);
        }

        @Override
        public DirectoryProperty directoryProperty() {
            return objectFactory.directoryProperty();
        }

        @Override
        public RegularFileProperty fileProperty() {
            return objectFactory.fileProperty();
        }

        @Override
        public <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException {
            return objectFactory.newInstance(type, parameters);
        }
    }
}
