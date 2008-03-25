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
    Object convention(Object convention, Map conventionMapping);

    Object conventionMapping(Map mapping);

    Object getProperty(String name);

    void setConvention(Object convention);

    Object getConvention();

    void setConventionMapping(Map conventionMapping);

    Object getConventionMapping();
}
