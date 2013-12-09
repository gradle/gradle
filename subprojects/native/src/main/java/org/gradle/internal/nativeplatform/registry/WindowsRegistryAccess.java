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
package org.gradle.internal.nativeplatform.registry;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import org.gradle.internal.UncheckedException;

/**
 * Provides access to a Windows registry
 */
public class WindowsRegistryAccess {
    private static final String REG_OPEN_KEY = "WindowsRegOpenKey";
    private static final String REG_CLOSE_KEY = "WindowsRegCloseKey";
    private static final String REG_QUERY_VALUE_EX = "WindowsRegQueryValueEx";
    private static final String REG_QUERY_INFO_KEY = "WindowsRegQueryInfoKey1";
    private static final String REG_ENUM_KEY_EX = "WindowsRegEnumKeyEx";
    private static final String REGISTRY_ENCODING = "UTF-16";
    private static final int KEY_READ = 0x20019;
    private static final int REG_SUCCESS = 0;

    private Preferences preferences;
    private Method regOpenKey;
    private Method regCloseKey;
    private Method regQueryValueEx;
    private Method regQueryInfoKey;
    private Method regEnumKeyEx;
    private int key = -1;

    WindowsRegistryAccess(int key, Preferences preferences) {
        try {
            Class<? extends Preferences> preferencesClass = preferences.getClass();

            regOpenKey = preferencesClass.getDeclaredMethod(REG_OPEN_KEY, new Class[] { int.class, byte[].class, int.class });
            regOpenKey.setAccessible(true);
            regCloseKey = preferencesClass.getDeclaredMethod(REG_CLOSE_KEY, new Class[] { int.class });
            regCloseKey.setAccessible(true);
            regQueryValueEx = preferencesClass.getDeclaredMethod(REG_QUERY_VALUE_EX, new Class[] { int.class, byte[].class });
            regQueryValueEx.setAccessible(true);
            regQueryInfoKey = preferencesClass.getDeclaredMethod(REG_QUERY_INFO_KEY, new Class[]{ int.class });
            regQueryInfoKey.setAccessible(true);
            regEnumKeyEx = preferencesClass.getDeclaredMethod(REG_ENUM_KEY_EX, new Class[] { int.class, int.class, int.class });
            regEnumKeyEx.setAccessible(true);

            this.key = key;
        } catch (NoSuchMethodException e) {
            this.key = -1;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public boolean isAvailable() {
        return key != -1;
    }

    public String readString(String subkey, String name) throws WindowsRegistryException {
        int handle = openKey(subkey);

        try {
            byte[] value = queryValueEx(handle, name);
            if (value == null) {
                throw new WindowsRegistryException("registry string " + subkey + "." + name + " not found");
            }

            return fromCString(value);
        } finally {
            closeKey(handle);
        }
    }

    public List<String> getSubkeys(String subkey) throws WindowsRegistryException {
        int handle = openKey(subkey);

        try {
            int[] info = queryInfoKey(handle);
            int count = info[0];
            int maxLength = info[3];
            List<String> subkeys = new ArrayList<String>(count);

            for (int i = 0; i != count; ++i) {
                subkeys.add(fromCString(enumKeyEx(handle, i, maxLength + 1)));
            }

            return subkeys;
        } finally {
            closeKey(handle);
        }
    }

    public int openKey(String subkey) throws WindowsRegistryException {
        try {
            int[] result = (int[]) regOpenKey.invoke(preferences, new Object[] {
                new Integer(key),
                toCString(subkey),
                KEY_READ
            });

            if (result[1] != REG_SUCCESS) {
                throw new WindowsRegistryException("registry key cannot be opened (" + result[1] + ")");
            }

            return result[0];
        } catch (IllegalAccessException e) {
            throw new WindowsRegistryException("registry key cannot be opened", e);
        } catch (IllegalArgumentException e) {
            throw new WindowsRegistryException("registry key cannot be opened", e);
        } catch (InvocationTargetException e) {
            throw new WindowsRegistryException("registry key cannot be opened", e);
        }
    }

    public void closeKey(int handle) throws WindowsRegistryException {
        try {
            regCloseKey.invoke(preferences, new Object[] {
                new Integer(handle)
            });
        } catch (IllegalAccessException e) {
            throw new WindowsRegistryException("registry key cannot be closed", e);
        } catch (IllegalArgumentException e) {
            throw new WindowsRegistryException("registry key cannot be closed", e);
        } catch (InvocationTargetException e) {
            throw new WindowsRegistryException("registry key cannot be closed", e);
        }
    }

    public byte[] queryValueEx(int handle, String valueName) throws WindowsRegistryException {
        try {
            return (byte[]) regQueryValueEx.invoke(preferences, new Object[] {
                new Integer(handle),
                toCString(valueName)
            });
        } catch (IllegalAccessException e) {
            throw new WindowsRegistryException("registry value cannot be queried", e);
        } catch (IllegalArgumentException e) {
            throw new WindowsRegistryException("registry value cannot be queried", e);
        } catch (InvocationTargetException e) {
            throw new WindowsRegistryException("registry value cannot be queried", e);
        }
    }

    public int[] queryInfoKey(int handle) throws WindowsRegistryException {
        try {
            return (int[]) regQueryInfoKey.invoke(preferences, new Object[] {
                new Integer(handle)
            });
        } catch (IllegalAccessException e) {
            throw new WindowsRegistryException("registry key information cannot be queried", e);
        } catch (IllegalArgumentException e) {
            throw new WindowsRegistryException("registry key information cannot be queried", e);
        } catch (InvocationTargetException e) {
            throw new WindowsRegistryException("registry key information cannot be queried", e);
        }
    }

    public byte[] enumKeyEx(int handle, int index, int maxLength) throws WindowsRegistryException {
        try {
            return (byte[]) regEnumKeyEx.invoke(preferences, new Object[] {
                new Integer(handle),
                new Integer(index),
                new Integer(maxLength + 1)
            });
        } catch (IllegalAccessException e) {
            throw new WindowsRegistryException("registry keys cannot be enumerated", e);
        } catch (IllegalArgumentException e) {
            throw new WindowsRegistryException("registry keys cannot be enumerated", e);
        } catch (InvocationTargetException e) {
            throw new WindowsRegistryException("registry keys cannot be enumerated", e);
        }
    }

    private static String fromCString(byte[] string) throws WindowsRegistryException {
        try {
            return new String(string, REGISTRY_ENCODING).trim();
        } catch (UnsupportedEncodingException e) {
            throw new WindowsRegistryException("unable to convert registry string to Java string", e);
        }
    }

    private static byte[] toCString(String string) throws WindowsRegistryException {
        try {
            return string.getBytes(REGISTRY_ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new WindowsRegistryException("unable to convert Java string to registry string", e);
        }
    }


}
