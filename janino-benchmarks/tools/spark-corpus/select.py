"""Selects the corpus of the workload SPARK_TPCDS from the output of "capture.sh": the complete generated code of a
fixed set of TPC-DS queries, one pass each (broadcast hash joins or sort merge joins), and copies it, with an
index, to the resources of "janino-benchmarks". The set covers aggregation, both join kinds, window functions,
rollups, set operations and subqueries, and includes the queries with the largest generated classes (q64, q66).

Usage: python select.py [<capture output dir> [<resources dir>]]
  defaults: "out" and "../../src/main/resources/org/codehaus/janino/benchmarks/tpcds", relative to this script.
"""
import io
import os
import shutil
import sys

# (query, pass): the pass alternates, so that both join kinds are represented.
SELECTION = [
    ('q1', 'broadcast'),
    ('q4', 'sortmerge'),
    ('q9', 'broadcast'),
    ('q14a', 'broadcast'),
    ('q23a', 'broadcast'),
    ('q47', 'broadcast'),
    ('q64', 'sortmerge'),
    ('q66', 'broadcast'),
    ('q72', 'sortmerge'),
    ('q88', 'broadcast'),
    ('q95', 'sortmerge'),
    ('q96', 'broadcast'),
]

HERE = os.path.dirname(os.path.abspath(__file__))
src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'out')
dst = (
    sys.argv[2] if len(sys.argv) > 2
    else os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'org', 'codehaus', 'janino', 'benchmarks', 'tpcds')
)

rows = []
for line in io.open(os.path.join(src, 'index.txt'), encoding='utf-8'):
    line = line.rstrip('\n')
    if not line or line.startswith('#'):
        continue
    parts = line.split('|')
    if (parts[1], parts[2]) in SELECTION:
        rows.append(parts)
rows.sort(key=lambda p: (SELECTION.index((p[1], p[2])), int(p[0].split('-')[2])))

if not os.path.isdir(dst):
    os.makedirs(dst)
for name in os.listdir(dst):
    if name.endswith('.java.txt'):
        os.remove(os.path.join(dst, name))

total = 0
for parts in rows:
    shutil.copyfile(os.path.join(src, parts[0]), os.path.join(dst, parts[0]))
    total += int(parts[5])

with io.open(os.path.join(dst, 'index.txt'), 'w', encoding='utf-8', newline='\n') as f:
    f.write('# The class bodies of the corpus; see README.txt. Written by tools/spark-corpus/select.py.\n')
    f.write('# file|query|pass|class|lines|bytes\n')
    for parts in rows:
        f.write('|'.join(parts) + '\n')

print('%d class bodies, %d bytes, written to %s' % (len(rows), total, dst))
