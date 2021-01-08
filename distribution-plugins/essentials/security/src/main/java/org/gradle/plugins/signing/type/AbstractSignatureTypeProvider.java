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
package org.gradle.plugins.signing.type;

import org.gradle.api.InvalidUserDataException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convenience base class for {@link SignatureTypeProvider} implementations.
 */
public abstract class AbstractSignatureTypeProvider implements SignatureTypeProvider {

    private String defaultTypeExtension;
    private final Map<String, SignatureType> types = new LinkedHashMap<String, SignatureType>();

    @Override
    public SignatureType getDefaultType() {
        return getTypeForExtension(defaultTypeExtension);
    }

    @Override
    public void setDefaultType(String defaultTypeExtension) {
        assertHasTypeForExtension(defaultTypeExtension);
        this.defaultTypeExtension = defaultTypeExtension;
    }

    @Override
    public boolean hasTypeForExtension(String extension) {
        return types.containsKey(extension);
    }

    @Override
    public SignatureType getTypeForExtension(String extension) {
        assertHasTypeForExtension(extension);
        return types.get(extension);
    }

    protected void register(SignatureType type) {
        types.put(type.getExtension(), type);
    }

    private void assertHasTypeForExtension(String extension) {
        if (!hasTypeForExtension(extension)) {
            throw new InvalidUserDataException("no signature type is registered for extension \'" + extension + "\'");
        }
    }
}
