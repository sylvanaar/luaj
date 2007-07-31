#!/bin/bash
LUA_HOME=/cygdrive/c/programs/lua5.1
DIRS="lua5.1-tests regressions"
for d in $DIRS; do

	# unpack files into the directory
	rm -rf $d
	mkdir -p $d
	jar -xvf $d.zip
	rm -f $d/*.luac
	
done