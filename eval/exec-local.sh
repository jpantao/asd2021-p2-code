#!/usr/bin/env bash

# ----------------------------------- CONSTANTS -------------------------------

RED='\033[0;31m'
BLUE='\033[0;34m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color


p2p_port=5000
server_port=6000

# ----------------------------------- PARSE PARAMS ----------------------------


POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
	--expname)
	expname="$2"
	shift
	shift
	;;
	--nclients)
    nclients="$2"
    shift # past argument
    shift # past value
    ;;
	--nservers)
    nservers="$2"
	shift # past argument
    shift # past value
    ;;
	--nthreads)
    nthreads="$2"
	shift # past argument
    shift # past value
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

if [[ -z "${nclients}" ]]; then
  echo "nclients not set"
	exit
fi
if [[ -z "${nservers}" ]]; then
  echo "nservers not set"
	exit
fi
if [[ -z "${nthreads}" ]]; then
  echo "nthreads not set"
	exit
fi
if [[ -z "${expname}" ]]; then
  echo "expname not set"
	exit
fi

mkdir -p results/${expname}

# ----------------------------------- LOG PARAMS ------------------------------
echo -e $BLUE"\n ---- CONFIG ---- " $NC
echo -e $GREEN" n servers: " $NC ${nservers}
echo -e $GREEN" n clients: " $NC ${nclients}
echo -e $GREEN" n threads: " $NC ${nthreads}
echo -e $BLUE" ---- END CONFIG ---- \n" $NC

sleep 5

# ----------------------------------- START EXP -------------------------------

servers_p2p="localhost:${p2p_port}"
servers_server="localhost:${server_port}"


i=1
while [ $i -lt $nservers ]; do
    servers_p2p="${servers_p2p},localhost:$(($p2p_port + $i))"
    servers_server="${servers_server},localhost:$(($server_port + $i))"
    i=$(($i + 1))
done

i=0
while [ $i -lt $nservers ]; do
  java -Dlog4j.configurationFile=server/log4j2.xml \
    -DlogFilename=${expname}/server_${nthreads}_${nservers}_$(($p2p_port + $i)) \
	  -cp server/asdProj2.jar Main -conf server/config.properties server_port=$(($server_port + $i)) \
	  n=$(($i + 1)) \
	  address=localhost p2p_port=$(($p2p_port + $i)) server_port=$(($server_port + $i)) \
		initial_membership=${servers_p2p} 2>&1 | sed "s/^/[$(($p2p_port + $i))] /" &
  echo "launched process on p2p port $(($p2p_port + $i)), server port $(($server_port + $i))"
  sleep 1
  i=$(($i + 1))
done

sleep 5

i=0
while [ $i -lt $nclients ]; do
  java -Dlog4j.configurationFile=client/log4j2.xml \
    -DlogFilename=${expname}/client_${nthreads}_${nservers}_${i} \
    -cp client/asd-client.jar site.ycsb.Client -t -s -P client/config.properties \
    -threads $nthreads -p fieldlength=1000 \
    -p hosts=${servers_server} -p readproportion=50 -p updateproportion=50 \
    > results/${expname}/${nthreads}_${nservers}_${i}.log 2>&1 | sed "s/^/[c-$node] /" &
    i=$(($i + 1))
done

sleep 400
kill $(ps aux | grep 'asdProj2.jar' | awk '{print $2}')
kill $(ps aux | grep 'asd-client.jar' | awk '{print $2}')
echo "All processes done!"
