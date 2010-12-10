/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins.jetty;

import org.gradle.api.InvalidUserDataException;

/**
 * Defines Jetty plugin system properties.
 *
 * @author Benjamin Muschko
 */
public class JettySystemProperty
{
    public static final String HTTP_PORT_SYSPROPERTY = "jetty.http.port";
    public static final String STOP_PORT_SYSPROPERTY = "jetty.stop.port";
    public static final String STOP_KEY_SYSPROPERTY = "jetty.stop.key";

    public static Integer getHttpPort() {
        String httpPortSystemProperty = System.getProperty(HTTP_PORT_SYSPROPERTY);

        if(httpPortSystemProperty != null) {
            try {
                return Integer.parseInt(httpPortSystemProperty);
            }
            catch(NumberFormatException e) {
                throw new InvalidUserDataException("Bad HTTP port provided as system property: " + httpPortSystemProperty, e);
            }
        }

        return null;
    }

    public static Integer getStopPort() {
        String stopPortSystemProperty = System.getProperty(STOP_PORT_SYSPROPERTY);

        if(stopPortSystemProperty != null) {
            try {
                return Integer.parseInt(stopPortSystemProperty);
            }
            catch(NumberFormatException e) {
                throw new InvalidUserDataException("Bad stop port provided as system property: " + stopPortSystemProperty, e);
            }
        }

        return null;
    }

    public static String getStopKey() {
        String stopKeySystemProperty = System.getProperty(STOP_KEY_SYSPROPERTY);

        if(stopKeySystemProperty != null) {
            return stopKeySystemProperty;
        }

        return null;
    }
}
