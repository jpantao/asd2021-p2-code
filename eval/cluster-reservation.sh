#! /bin/bash

oarsub -l {"host in ('node16', 'node21', 'node22', 'node23')"}/nodes=4,walltime=4:00 "eval/run-experiments.sh"