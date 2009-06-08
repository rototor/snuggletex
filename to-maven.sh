#!/bin/sh -e
#
# (This is no longer required but might be useful for
# plonking other things into the Maven repo, such as Saxon?)

MAVEN_REPO=/Disk/lust3/maven2
SNUGGLETEX_BASE=$MAVEN_REPO/uk/ac/ed/ph/snuggletex/snuggletex
VERSION=1.1-SNAPSHOT
DEST_FILE=$SNUGGLETEX_BASE/$VERSION/snuggletex-$VERSION.jar

cp dist/snuggletex.jar $DEST_FILE
sha1sum $DEST_FILE >$DEST_FILE.sha1
