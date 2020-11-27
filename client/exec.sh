nthreads=$1
payload=$2
servers=$3
readsper=$4
writesper=$5

shift 5

java -Dlog4j.configurationFile=log4j2.xml \
  -DlogFilename=clientLog.log \
	-cp asd-client.jar site.ycsb.Client -t -s -P config.properties \
	-threads $nthreads -p fieldlength=$payload \
	-p hosts=$servers \
	-p readproportion=${readsper} -p updateproportion=${writesper} \
	"$@" \
	2> client.log