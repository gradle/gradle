package org.gradle.wrapper;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: hans
 * Date: Mar 27, 2008
 * Time: 2:52:53 PM
 * To change this template use File | Settings | File Templates.
 */
public interface IDownload {
    void download(String address, File destination) throws Exception;
}
