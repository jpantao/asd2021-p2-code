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

allnodes=`uniq $OAR_NODEFILE`
#allnodes=`./nodes.sh`
nnodes=`wc -l <<< $allnodes`

if (( $nclients + $nservers > $nnodes )); then
	echo -e $RED"Not enough nodes!"$NC
	exit
fi

clientnodes=`tail -n $nclients <<< $allnodes`
servernodes=`head -n $nservers <<< $allnodes`

mkdir -p logs/${expname}
mkdir -p results/${expname}

servers_p2p=""
servers_server=""
for snode in $servernodes; do
	ip=`getent hosts $snode.cluster.di.fct.unl.pt | awk '{print $1}'`
	servers_p2p=${servers_p2p}${ip}:${p2p_port}","
	servers_server=${servers_server}${ip}:${server_port}","
done
servers_p2p=${servers_p2p::-1}
servers_server=${servers_server::-1}

# ----------------------------------- LOG PARAMS ------------------------------
echo -e $BLUE"\n ---- CONFIG ---- " $NC
echo -e $GREEN" servers (${nservers}): " $NC	$servernodes
echo -e $GREEN" clients (${nclients}): " $NC	$clientnodes
echo -e $GREEN" n threads: " $NC ${nthreads}
echo -e $GREEN" initial_membership: " $NC ${servers_p2p}
echo -e $GREEN" client_connection_points: " $NC ${servers_server}
echo -e $BLUE" ---- END CONFIG ---- \n" $NC

sleep 5

# ----------------------------------- START EXP -------------------------------


echo -e $BLUE "Starting servers and sleeping 15" $NC

for servernode in $servernodes
do
	oarsh $servernode "mkdir -p logs/${expname}; \
			java \
				-Dlog4j.configurationFile=config/log4j2.xml \
				-DlogFilename=logs/${expname}/server_${nthreads}_${nservers}_${servernode} \
				-cp server/asdProj2.jar Main -conf config.properties server_port=${server_port} \
				p2p_port=${p2p_port} interface=bond0 \
				initial_membership=${servers_p2p}" 2>&1 | sed "s/^/[s-$servernode] /" &
	sleep 1
done

sleep 15

echo -e $BLUE "Starting clients and sleeping 70" $NC

for node in $clientnodes
do
	oarsh $node "mkdir -p logs/${expname}; \
		mkdir -p results/${expname}; \
		java -Dlog4j.configurationFile=log4j2.xml \
				-DlogFilename=logs/${expname}/client_${nthreads}_${nservers}_${node} \
				-cp client/asd-client.jar site.ycsb.Client -t -s -P client/config.properties \
				-threads $nthreads -p fieldlength=1000 \
				-p hosts=$servers_server -p readproportion=50 -p updateproportion=50 \
				> results/${expname}/${nthreads}_${nservers}_${node}.log" 2>&1 | sed "s/^/[c-$node] /" &
done

sleep 70

echo "Killing clients"
for node in $clientnodes
do
	oarsh $node "pkill java" &
done
echo "Clients Killed"

sleep 1

echo "Killing servers"
for servernode in $servernodes
do
	oarsh $servernode "pkill java" &
done
echo "Servers Killed"

sleep 1

echo "Done!"

exit
