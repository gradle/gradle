/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publisher;

import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.QName;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GUtil;

import java.io.File;

public class ValidatingIvyPublisher implements IvyPublisher {
    private final IvyPublisher delegate;

    public ValidatingIvyPublisher(IvyPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(IvyNormalizedPublication publication, IvyArtifactRepository repository) {
        validateIdentity(publication.getModule());
        validateIvyDescriptorFileCoordinates(publication.getModule(), publication.getDescriptorFile());
        delegate.publish(publication, repository);
    }

    private void validateIdentity(Module module) {
        checkNonEmpty("organisation", module.getGroup());
        checkNonEmpty("module name", module.getName());
        checkNonEmpty("revision", module.getVersion());
    }

    private void checkNonEmpty(String name, String value) {
        if (!GUtil.isTrue(value)) {
            throw new InvalidUserDataException(String.format("The %s value cannot be empty", name));
        }
    }

    private void validateIvyDescriptorFileCoordinates(Module module, File ivyFile) {
        Module ivyXmlModule = parseIvyFileIntoModule(ivyFile);
        checkMatches("organisation", module.getGroup(), ivyXmlModule.getGroup());
        checkMatches("module name", module.getName(), ivyXmlModule.getName());
        checkMatches("revision", module.getVersion(), ivyXmlModule.getVersion());
    }

    private void checkMatches(String name, String projectIdentityValue, String pomFileValue) {
        if (!projectIdentityValue.equals(pomFileValue)) {
            throw new InvalidUserDataException(String.format("Publication %1$s does not match ivy descriptor. Cannot edit %1$s directly in the ivy descriptor file.", name));
        }
    }

    private Module parseIvyFileIntoModule(File ivyFile) {
        Node rootNode;
        try {
            // TODO:DAZ Turn on XML validation, and add a schema to the generated ivy file
            rootNode = new XmlParser().parse(ivyFile);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }

        Node infoNode = (Node) rootNode.getAt(new QName("info")).get(0);
        return new DefaultModule((String) infoNode.attribute("organisation"), (String) infoNode.attribute("module"), (String) infoNode.attribute("revision"));
    }

}
