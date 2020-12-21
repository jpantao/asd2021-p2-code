#! /bin/bash

oarsub -I -l {"host in ('node16', 'node21', 'node22', 'node23')"}/nodes=4,walltime=6:00