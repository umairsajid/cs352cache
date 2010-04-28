#!/bin/sh
#

# Build tool to compile the source and run it with given arguments
clean=0
compile=0
debug=0

# First check to see if our args are correct
while getopts c:b:a:w:m:d: opt
do 
    case "$opt" in
        c)  capacity=$OPTARG;;
	b)  blocksize=$OPTARG;;
	a)  associativity=$OPTARG;;
	w)  clean=1;;
	m)  compile=1;;
	d)  debug=1;;
	\?)  echo "Usage: $0 -c -b -a [-w1][-m1][-d1]"; exit 1;;
    esac
done

# Clean files if we have a clean flag and don't run
if [ $clean -eq 1 ]
then
    rm *.class; exit 0;
fi

# Only compile but do not run
if [ $compile -eq 1 ]
then
    javac -g cache_sim.java; exit 0;
fi

# Run in debug mode
if [ $debug -eq 1 ]
then
    javac -g cache_sim.java
    jdb cache_sim.class; exit 0;
fi

#Otherwise we compile and run with the given arguments
javac -g cache_sim.java
java cache_sim -c$capacity -b$blocksize -a$associativity