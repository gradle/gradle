#!/bin/bash

RUNASUSER=$1

if test "${RUNASUSER}" = ""
then
    echo "Usage: `basename $0` <username>"
    exit 1
fi

SCRIPTDIR=`dirname $0`
SCRIPTDIR=`cd ${SCRIPTDIR}; pwd`
sudo -u ${RUNASUSER} ${SCRIPTDIR}/playBinary

