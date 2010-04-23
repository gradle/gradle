package org.gradle.api.plugins.announce;

import org.gradle.api.Project;

/**
 * @author Hans Dockter
 */
public interface AnnouncerFactory {
    Announcer createAnnouncer(String type);
}
