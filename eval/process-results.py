#!/usr/bin/env python3

import os
import csv
import numpy as np
import matplotlib.pyplot as plt

PAXOS_DIR = "./results/paxos"
MPAXOS_DIR = "./results/multi-paxos"

NSERVERS = 3
CLIENTS = ['node16']
NTHREADS = [3, 6, 9, 12, 15, 18, 21, 24, 27, 30]


def extract_values(experience_dir):
    thrp = list()
    avgl = list()

    for nthread in NTHREADS:
        client_throughput = list()
        client_avglatency = list()
        for client in CLIENTS:
            with open(f'{experience_dir}/{nthread}_{NSERVERS}_{client}.log', newline='') as logfile:
                reader = csv.reader(logfile, delimiter=',')
                for row in reader:
                    if len(row) != 3 or "[CLEANUP]" in row[0]:
                        continue
                    if "[OVERALL]" in row[0] and "Throughput(ops/sec)" in row[1]:
                        client_throughput.append(float(row[2]))
                    elif "AverageLatency(us)" in row[1]:
                        client_avglatency.append(float(row[2]) / 1000)

        thrp.append(np.array(client_throughput).sum())
        avgl.append(np.array(client_avglatency).mean())

    return thrp, avgl


def generate_plot(throughput_values, avglatency_values, title):
    plt.plot(throughput_values, avglatency_values, '-o')
    plt.title(title)
    plt.xlabel("Throughput (ops/sec)")
    plt.ylabel("Latency (ms)")
    plt.savefig(f'plots/{title.lower()}.pdf')
    plt.show()


def log_values(throughput, avglatency):
    print("--- Paxos ---")
    print(f"throughput -> {throughput}")
    print(f"avglatency -> {avglatency}")
    print("-------------")


if __name__ == '__main__':
    throughput, avglatency = extract_values(PAXOS_DIR)
    generate_plot(throughput, avglatency, "Paxos")
    log_values(throughput, avglatency)
    # throughput, avglatency = extract_values(MPAXOS_DIR)
    # generate_plot(throughput, avglatency, "Multi Paxos")
    # log_values(throughput, avglatency)

