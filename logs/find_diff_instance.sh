#!/bin/bash

cat logs/node5000.log | grep Decided | awk '{print $4 "\t" $8}' > /tmp/5000
cat logs/node5001.log | grep Decided | awk '{print $4 "\t" $8}' > /tmp/5001
cat logs/node5002.log | grep Decided | awk '{print $4 "\t" $8}' > /tmp/5002

echo "diff 5000 5001"
diff /tmp/5000 /tmp/5001 | head -n 4

echo "diff 5000 5002"
diff /tmp/5000 /tmp/5002 | head -n 4

echo "diff 5001 5002"
diff /tmp/5001 /tmp/5002 | head -n 4
