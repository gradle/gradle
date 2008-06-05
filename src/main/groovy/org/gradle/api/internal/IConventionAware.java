package org.gradle.api.internal;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: hans
 * Date: Feb 13, 2008
 * Time: 8:14:40 AM
 * To change this template use File | Settings | File Templates.
 */
public interface IConventionAware {
    Object conventionMapping(Map mapping);

    Object getProperty(String name);

    void setConventionMapping(Map conventionMapping);

    Object getConventionMapping();
}
