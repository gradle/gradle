/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project

import org.apache.tools.ant.PropertyHelper
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeEvent

public class AntBuilder extends groovy.util.AntBuilder {

    public void setProperty(String property, Object newValue) {
        if (metaClass.hasProperty(this, property)) {
            metaClass.setProperty(this, property, newValue)
            return
        }
        doSetProperty(property, newValue)
    }

    private def doSetProperty(String property, newValue) {
        PropertyHelper.getPropertyHelper(project).setUserProperty(null, property, newValue)
    }

    def propertyMissing(String name) {
        if (project.properties.containsKey(name)) {
            return project.properties[name]
        }
        throw new MissingPropertyException(name, getClass())
    }

    public Map getProperties() {
        ObservableMap map = new ObservableMap(project.properties)
        map.addPropertyChangeListener({PropertyChangeEvent event -> doSetProperty(event.propertyName, event.newValue) } as PropertyChangeListener)
        map
    }

    public Map getReferences() {
        ObservableMap map = new ObservableMap(project.references)
        map.addPropertyChangeListener({PropertyChangeEvent event -> project.addReference(event.propertyName, event.newValue) } as PropertyChangeListener)
        map
    }

}
