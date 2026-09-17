// list-topics.mjs - show the Order Fulfillment System's topics, how many records each holds,
// and where each consumer group has read up to.
//
//   node list-topics.mjs           one snapshot
//   node list-topics.mjs --watch   refresh every 3s until Ctrl-C
//
// "lag" is what a topic is holding that its consumer has not processed yet. During a healthy
// run it flicks above zero and settles back to zero; a lag that stays put means the consuming
// app is down, stuck, or dead-lettering.

import { Kafka } from 'kafkajs';

// kafkajs computes a negative internal timeout on current Node versions and prints a
// TimeoutNegativeWarning on every run. It is cosmetic and unrelated to anything here, but it
// buries the output of a script people run constantly, so this one warning is filtered.
const emitWarning = process.emitWarning;
process.emitWarning = (warning, ...rest) => {
  const name = typeof warning === 'object' ? warning.name : rest[0];
  if (name === 'TimeoutNegativeWarning') return;
  emitWarning(warning, ...rest);
};


const BROKERS = (process.env.KAFKA_BOOTSTRAP_SERVERS || '127.0.0.1:9092').split(',');
const WATCH = process.argv.includes('--watch');

const kafka = new Kafka({ clientId: 'order-system-topic-viewer', brokers: BROKERS, logLevel: 1 });
const admin = kafka.admin();

const num = (v) => Number(v);

async function snapshot() {
  const topics = (await admin.listTopics()).filter((t) => !t.startsWith('__')).sort();

  // depth per topic: high water mark minus low water mark, summed over partitions
  const depth = new Map();
  for (const t of topics) {
    const offsets = await admin.fetchTopicOffsets(t);
    depth.set(t, offsets.reduce((a, p) => a + (num(p.high) - num(p.low)), 0));
  }

  // committed position per consumer group, so we can show lag
  const consumed = new Map(); // topic -> [{group, offset}]
  const { groups } = await admin.listGroups();
  for (const g of groups) {
    let offsets;
    try {
      offsets = await admin.fetchOffsets({ groupId: g.groupId });
    } catch {
      continue;
    }
    for (const o of offsets) {
      const committed = o.partitions.reduce((a, p) => a + Math.max(0, num(p.offset)), 0);
      if (!consumed.has(o.topic)) consumed.set(o.topic, []);
      consumed.get(o.topic).push({ group: g.groupId, offset: committed });
    }
  }

  const W = Math.max(...topics.map((t) => t.length), 24);
  console.log(`\n${new Date().toISOString()}   broker ${BROKERS.join(',')}`);
  console.log(`${'TOPIC'.padEnd(W)}  ${'RECORDS'.padStart(7)}  ${'LAG'.padStart(5)}  CONSUMER GROUP`);
  console.log('-'.repeat(W + 40));

  for (const t of topics) {
    const records = depth.get(t) ?? 0;
    const readers = consumed.get(t) ?? [];
    if (readers.length === 0) {
      const note = t.endsWith('.dlq') ? '(dead letters - nothing consumes these)' : '(no consumer)';
      console.log(`${t.padEnd(W)}  ${String(records).padStart(7)}  ${'-'.padStart(5)}  ${note}`);
      continue;
    }
    readers.forEach((r, i) => {
      const lag = Math.max(0, records - r.offset);
      const flag = lag > 0 ? ' <-- pending' : '';
      console.log(
        `${(i === 0 ? t : '').padEnd(W)}  ${String(i === 0 ? records : '').padStart(7)}  ` +
          `${String(lag).padStart(5)}  ${r.group}${flag}`
      );
    });
  }

  const dlq = topics.filter((t) => t.endsWith('.dlq') && (depth.get(t) ?? 0) > 0);
  if (dlq.length > 0) {
    console.log(`\n  WARNING: dead letters present in ${dlq.join(', ')}`);
  }
}

await admin.connect();
try {
  if (WATCH) {
    for (;;) {
      await snapshot();
      await new Promise((r) => setTimeout(r, 3000));
    }
  } else {
    await snapshot();
  }
} finally {
  await admin.disconnect();
}
