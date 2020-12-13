#!/bin/bash

cat logs/node$1.log | grep '{ins='"$2"',\|: '"$2"' -\|Propose: '"$2"'  -'
