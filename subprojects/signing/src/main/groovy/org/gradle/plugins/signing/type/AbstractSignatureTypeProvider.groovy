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
package org.gradle.plugins.signing.type

import org.gradle.api.InvalidUserDataException

abstract class AbstractSignatureTypeProvider implements SignatureTypeProvider {
    
    private String defaultTypeExtension
    private Map<String, SignatureType> types = [:]
    
    SignatureType getDefaultType() {
        getTypeForExtension(defaultTypeExtension)
    }
    
    void setDefaultType(String defaultTypeExtension) {
        getTypeForExtension(defaultTypeExtension) // verify we have this extension
        this.defaultTypeExtension = defaultTypeExtension
    }
    
    SignatureType getTypeForExtension(String extension) {
        if (!types.containsKey(extension)) {
            throw new InvalidUserDataException("no signature type is registered for extension '$extension'")
        }
        types[extension]
    }
    
    protected void register(SignatureType type) {
        types[type.extension] = type
    }
    
    boolean hasTypeForExtension(String extension) {
        types.containsKey(extension)
    }
}