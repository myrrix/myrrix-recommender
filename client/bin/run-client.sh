#!/bin/bash
# Copyright Myrrix Ltd. Licensed under the Apache License, Version 2.0.
# See http://myrrix.com/legal

# This is a utility script which may be set with appropriate values, to avoid re-typing them every time
# on the command line. It illustrates usage as well.

# Set to the hostname or IP address, and port, of the Serving Layer instance to query.
# Set these when using one Serving Layer. When using multiple partitioned Serving Layers, instead
# set ALL_PARTITIONS
HOST=localhost
PORT=8080
#ALL_PARTITIONS=foo:80,foo2:8080;bar:8080;baz2:80,baz3:80
#ALL_PARTITIONS=auto

# Set if the Serving Layer requires HTTPS
#SECURE=true

# Set these values if the Serving Layer requires a username and password to access
#USERNAME=username
#PASSWORD=password

# Set if translating user IDs
#TRANSLATE_USER=true

# Set to file containing item IDs if translating item IDs
#TRANSLATE_ITEM=/path/to/file.txt

# Set any other args here
#OTHER_ARGS=

# ----- Nothing to set below here -------

if [ -n "${HOST}" ]; then
  ALL_ARGS="--host=${HOST} --port=${PORT}"
else
  ALL_ARGS="--allPartitions=${ALL_PARTITIONS}"
fi

if [ -n "${SECURE}" ]; then
  ALL_ARGS="${ALL_ARGS} --secure"
fi

if [ -n "${USERNAME}" ]; then
  ALL_ARGS="${ALL_ARGS} --userName=${USERNAME}"
fi
if [ -n "${PASSWORD}" ]; then
  ALL_ARGS="${ALL_ARGS} --password=${PASSWORD}"
fi

if [ -n "${TRANSLATE_USER}" ]; then
  ALL_ARGS="${ALL_ARGS} --translateUser"
fi
if [ -n "${TRANSLATE_ITEM}" ]; then
  ALL_ARGS="${ALL_ARGS} --translateItem=${TRANSLATE_ITEM}"
fi

if [ -n "${OTHER_ARGS}" ]; then
  ALL_ARGS="${ALL_ARGS} ${OTHER_ARGS}"
fi


CLIENT_JAR=`ls myrrix-client-*.jar`

java -jar ${CLIENT_JAR} ${ALL_ARGS} $@
