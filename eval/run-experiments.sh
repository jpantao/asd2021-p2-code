#!/usr/bin/env bash

nclinents=(3 6 9 12 15 18 21 24 27 30 80 90 100)


for n in "${nclinents[@]}"
do
  echo "Running multi-paxos experiment for $n client threads..."
  ./eval/exec.sh --nclients 1 --nservers 3 --nthreads "$n" --expname paxos
  wait
  echo "Finished!"
done


for n in "${nclinents[@]}"
do
  echo "Running multi-paxos experiment for $n client threads..."
  ./eval/exec.sh --nclients 1 --nservers 3 --nthreads "$n" --expname multi-paxos
  wait
  echo "Finished!"
done


echo "All experiments finished!"
