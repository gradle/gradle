/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.artifact.ant.AntDownloadMonitor;
import org.apache.maven.artifact.manager.DefaultWagonManager;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.tools.ant.Project;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.lang.reflect.Field;

public class LoggingHelper {
    public static void injectLogger(PlexusContainer container, Project project) {
        try {
            WagonManager wagonManager = (WagonManager) container.lookup(WagonManager.ROLE);
            Field field = DefaultWagonManager.class.getDeclaredField("downloadMonitor");
            field.setAccessible(true);
            AntDownloadMonitor antDownloadMonitor = (AntDownloadMonitor) field.get(wagonManager);
            antDownloadMonitor.setProject(project);
        } catch (ComponentLookupException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
