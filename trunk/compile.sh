#!/bin/sh
#

# Build tool to compile the source and run it with given arguments

# First check to see if there is the correct number of arguments
# This doesn't work yet, I'll work on it later
#if [ $# -gt 3 | $# -lt 3 ] ; then
#    echo "THERE AREN'T THREE ARGS";
#    exit 1
#fi

while getopts c:b:a: opt
do 
    case "$opt" in
        c)  capacity=$OPTARG;;
	b)  blocksize=$OPTARG;;
	a)  associativity=$OPTARG;;
	\?)  echo "Usage: $0 -c -b -a"; exit 1;;
    esac
done

javac cache_sim.java
java cache_sim -c$capacity -b$blocksize -a$associativity