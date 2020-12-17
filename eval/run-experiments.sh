#!/usr/bin/env bash

nclinents=(3 6 9 12 15 18)

for n in "${nclinents[@]}"
do
  echo "Running experiment for $n client threads..."
  ./exec-local.sh --nclients 1 --nservers 3 --nthreads "$n" --expname paxos
  wait
  echo "Finished!"
done

echo "All experiments finished!"