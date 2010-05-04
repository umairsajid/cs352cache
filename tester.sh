#!/bin/sh
#

# an automator for testing this god-awful project

#I'm just going to do a million for loops over every combination, because fuck this ho, I'm going to figure out what the best configuration is
#skeet skeet, goddamn
echo $1
mkdir "Assoc$1traces"
for capacity in 4 8 16 32 64
do
    for blocksize in 4 8 16 32 64 128 256 512
    do
	java cache_sim -c$capacity -b$blocksize -a$1 < cs352.trace >  "Assoc$1traces"/"b$blocksize-c$capacity.out"
    done
done