/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal

import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.Unmanaged
import org.gradle.model.internal.manage.schema.extract.ScalarTypes
import org.gradle.model.internal.type.ModelType

trait ModelValidationTypes {
    def classLoader = new GroovyClassLoader(getClass().classLoader)
    static final List<Class<? extends Serializable>> JDK_SCALAR_TYPES = ScalarTypes.TYPES.rawClass

    @Managed
    static interface NonStringProperty {
        Object getName()

        void setName(Object name)
    }

    @Managed
    static interface OnlyGetter {
        String getName()
    }

    @Managed
    static interface ClassWithExtendedFileType {
        ExtendedFile getExtendedFile()

        void setExtendedFile(ExtendedFile extendedFile)
    }

    static class ExtendedFile extends File {
        ExtendedFile(String pathname) {
            super(pathname)
        }
    }

    @Managed
    static interface MissingUnmanaged {
        InputStream getThing();

        void setThing(InputStream inputStream);
    }

    @Managed
    static interface ExtendsMissingUnmanaged {
        @Unmanaged
        InputStream getThing();

        void setThing(InputStream inputStream);
    }

    @Managed
    static abstract class UnmanagedModelMapInManagedType {
        abstract ModelMap<InputStream> getThings()
    }

    static abstract class UnmanagedModelMapInUnmanagedType {
        ModelMap<InputStream> getThings() { null }
    }

    @Managed
    static interface OnlyGetGetter {
        boolean getThing()
    }

    @Managed
    static interface OnlyIsGetter {
        boolean isThing()
    }

    Class<?> managedClassWithoutSetter(Class<?> type) {
        String typeName = type.getSimpleName()
        return classLoader.parseClass("""
import org.gradle.model.Managed

@Managed
interface Managed${typeName} {
    ${typeName} get${typeName}()
}
""")
    }

    Class<?> managedClass(Class<?> type) {
        String typeName = type.getSimpleName()
        return classLoader.parseClass("""
import org.gradle.model.Managed

@Managed
interface Managed${typeName} {
    ${typeName} get${typeName}()
    void set${typeName}(${typeName} a${typeName})
}
""")
    }

    String getName(ModelType<?> modelType) {
        modelType
    }

    String getName(Class<?> clazz) {
        clazz.name
    }

    String canNotBeConstructed(String type) {
        "A model node of type: '${type}' can not be constructed."
    }
}
