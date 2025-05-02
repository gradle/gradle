/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Base64;

/**
 * Represents an identifier containing a tuple of namespace and name for use when
 * consuming/producing namespaced elements in descriptors.
 */
public class NamespaceId implements Serializable {
    private String namespace;
    private String name;

    public static NamespaceId decode(String encoding) {
        byte[] data = Base64.getDecoder().decode(encoding);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data); DataInputStream dis = new DataInputStream(bais)) {
            String namespace = dis.readUTF();
            String name = dis.readUTF();
            return new NamespaceId(namespace, name);
        } catch (Exception e) {
            throw new RuntimeException("Failed decoding namespace ID");
        }
    }

    public NamespaceId(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public String encode() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(namespace);
            dos.writeUTF(name);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed encoding namespace ID '" + name + "'");
        }
    }

    /**
     * Gets the namespace for this identifier.
     *
     * @return the namespace for this identifier
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace for this identifier.
     *
     * @param namespace the namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the name for this identifier.
     *
     * @return the name for this identifier
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name for this identifier.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o == null || o.getClass() != getClass()) {
            return false;
        }

        NamespaceId other = (NamespaceId) o;
        return other.getName().equals(name) && other.getNamespace().equals(namespace);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31)
                .append(namespace)
                .append(name)
                .toHashCode();
    }
}
