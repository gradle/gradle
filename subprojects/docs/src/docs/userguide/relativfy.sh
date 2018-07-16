#!/bin/bash

for adoc_file in *.adoc; do
    echo "Processing $adoc_file..."
    sed -e "s/<<#/<<${adoc_file}#/g" $adoc_file > "${adoc_file}.tmp"
    mv "${adoc_file}.tmp" $adoc_file
done
