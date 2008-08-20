/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.build.release

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.tigris.subversion.javahl.Revision
import org.tigris.subversion.javahl.Status
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory
import org.tmatesoft.svn.core.internal.util.SVNPathUtil
import org.tmatesoft.svn.core.javahl.SVNClientImpl
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNStatusClient
import org.tmatesoft.svn.core.wc.SVNWCUtil

/**
 * @author Hans Dockter
 */
class Svn {
    private static Logger logger = LoggerFactory.getLogger(Svn)

    Project project
    SVNClientManager clientManager
    SVNStatusClient statusClient
    SVNClientImpl javaHlClient
    boolean alwaysTrunk = false

    Svn() {}

    Svn(Project project) {
        assert project
        this.project = project
        try {
            DAVRepositoryFactory.setup();
            clientManager = SVNClientManager.newInstance(
                    SVNWCUtil.createDefaultOptions(true), project.codehausUserName, project.codehausUserPassword)
            statusClient = clientManager.getStatusClient()
            javaHlClient = SVNClientImpl.newInstance()
            javaHlClient.username(project.codehausUserName)
            javaHlClient.password(project.codehausUserPassword)
            throwExceptionIfNoSvnProject()
        } catch (Throwable e) {
            logger.info("""Can't access svn working copy. Maybe this is not an svn project or the codehausUserName/password property is not set.
                Releasing won't be possible. It is assumed that this isTrunk is true.""")
            alwaysTrunk = true
        }
    }

    void throwExceptionIfNoSvnProject() {
        svnDir
    }

    def release() {
        checkWorkingCopy()
        if (isTrunk() || project.version.toString() == branchVersion) {
            majorOrMinorRelease()
        } else {
            revisionRelease()
        }
    }

    def majorOrMinorRelease() {
        if (isTrunk()) {
            exitIfReleaseBranchDirectoryExists()
        }
        project.version.storeCurrentVersion()
        commitProperties()
        if (isTrunk()) {
            copyTrunkToReleaseBranch()
        }
        tagReleaseBranch()
    }

    def revisionRelease() {
        project.version.storeCurrentVersion()
        commitProperties()
        tagReleaseBranch()
    }


    def commitProperties() {
        File props = new File(project.projectDir, 'gradle.properties')
        javaHlClient.commit([props.absolutePath] as String[], "Incremented version properties", false)
    }

    def exitIfReleaseBranchDirectoryExists() {
        try {
            clientManager.WCClient.doInfo(new SVNURL(releaseBranchUrl, true),
                    SVNRevision.UNDEFINED, SVNRevision.HEAD)
            throw new GradleException("Release branch directory $releaseBranchUrl already exists. You can't release from trunk therefore.")
        } catch (SVNException ignore) {
            // SVNException means directory does not exists. Which is what we want.
        }
    }

    def copyTrunkToReleaseBranch() {
        javaHlClient.copy(trunkUrl, releaseBranchUrl, "Copy trunk to release branch $releaseBranchName" as String, Revision.HEAD)
    }

    def tagReleaseBranch() {
        javaHlClient.copy(releaseBranchUrl, tagsUrl, "Tag release $releaseTagName" as String, Revision.HEAD)
    }

    def getReleaseBranchName() {
        'RB-' + majorMinorVersion
    }

    def getReleaseTagName() {
        'REL-' + project.version
    }

    def getMajorMinorVersion() {
        project.version.toString().split('\\.')[0..1].join('.')
    }

    def getReleaseBranchUrl() {
        svnBaseUrl + "/branches/$releaseBranchName"
    }

    def getTagsUrl() {
        svnBaseUrl + "/tags/$releaseTagName"
    }

    def getTrunkUrl() {
        svnBaseUrl + "/trunk"
    }

    String getSvnUrl() {
        statusClient.doStatus(project.projectDir, false).URL
    }

    String getSvnBaseUrl() {
        String url = svnUrl
        url = SVNPathUtil.removeTail(url)
        if (!isTrunk()) {
            url = SVNPathUtil.removeTail(url)
        }
        url
    }

    boolean isTrunk() {
        alwaysTrunk || svnDir == 'trunk'
    }

    String getSvnDir() {
        svnUrl.substring(svnUrl.lastIndexOf('/') + 1)
    }

    String getBranchVersion() {
        svnDir.replaceFirst('RB-', '')
    }

    void checkWorkingCopy() {
        List statuses = javaHlClient.status(project.projectDir.absolutePath, true, true, false)
//        ISVNStatusHandler statusHandler = {SVNStatus status -> statuses << status} as ISVNStatusHandler
//        statusClient.doStatus(project.projectDir, true, true, false, false, statusHandler)
        if (statuses) {
            String result = ''
            statuses.each {Status status -> result += status.path + ' ' + status.url.toString() + '\n'}
            throw new GradleException("The working copy is not in sync with the SVN server. We can't release!\n$result")
        }
    }
}
