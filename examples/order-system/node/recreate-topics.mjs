// recreate-topics.mjs - delete the named topics if they exist, then create them fresh.
//
//   node recreate-topics.mjs --all                     every topic the system uses, + DLQs
//   node recreate-topics.mjs --all --if-missing        repair a broker that lost some
//   node recreate-topics.mjs <topic> [<topic> ...]     just these
//   node recreate-topics.mjs --if-missing <topic> ...  create only what is absent
//
// --all is the single source of truth for the topology. Keeping it here rather than spread across
// the per-project boot scripts means one place to add a topic, and no chance of two scripts
// disagreeing about who owns one.
//
// Deletion is what makes a boot script repeatable: a topic left over from an earlier run still
// holds its records and its consumer-group offsets, so a "clean" start would replay stale events.
// Kafka deletes asynchronously, so each delete is polled until the topic is really gone before
// the topic is recreated.

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
const PARTITIONS = Number(process.env.KAFKA_TOPIC_PARTITIONS || 1);
const ts = () => new Date().toISOString();

// Every topic in the system. The DLQ counterpart is derived, never listed separately - a binding
// without its dead-letter topic is the failure that hides until something actually fails.
const ALL_TOPICS = [
  'orders.inbound',         // external party  -> manager   (the real ingress)
  'fulfillment.request',    // manager         -> steward
  'fulfillment.tick',       // steward         -> steward   (dispatch window)
  'fulfillment.ack',        // acknowledge     -> steward
  'fulfillment.status',     // steward         -> manager   (read-model feed)
  'sor.dispatch',           // steward         -> SoR
  'sor.validation',         // SoR             -> acknowledge
  'sor.preprocess',         // SoR             -> acknowledge
  'sor.process',            // SoR             -> acknowledge
  'order.status.external',  // manager         -> external party
];

const IF_MISSING = process.argv.includes('--if-missing');
const ALL = process.argv.includes('--all');
const named = process.argv.slice(2).filter((a) => !a.startsWith('--'));
const wanted = ALL ? ALL_TOPICS.flatMap((t) => [t, `${t}.dlq`]) : named;

if (wanted.length === 0) {
  console.error('usage: node recreate-topics.mjs --all | <topic> [<topic> ...]  [--if-missing]');
  process.exit(1);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const kafka = new Kafka({ clientId: 'order-system-topic-admin', brokers: BROKERS, logLevel: 1 });
const admin = kafka.admin();

async function waitUntilGone(topics, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const existing = await admin.listTopics();
    const left = topics.filter((t) => existing.includes(t));
    if (left.length === 0) return;
    if (Date.now() > deadline) {
      throw new Error(`timed out waiting for deletion of: ${left.join(', ')}`);
    }
    await sleep(500);
  }
}

await admin.connect();
try {
  const existing = await admin.listTopics();

  // --if-missing repairs a broker that lost topics (kafka-standalone wipes them on restart)
  // without disturbing topics that live consumers are already attached to.
  if (IF_MISSING) {
    const absent = wanted.filter((t) => !existing.includes(t));
    if (absent.length === 0) {
      console.log(`[${ts()}] all ${wanted.length} topic(s) already present`);
    } else {
      await admin.createTopics({
        topics: absent.map((topic) => ({ topic, numPartitions: PARTITIONS, replicationFactor: 1 })),
        waitForLeaders: true,
        timeout: 15000,
      });
      console.log(`[${ts()}] created missing: ${absent.join(', ')}`);
    }
    await admin.disconnect();
    process.exit(0);
  }

  const toDelete = wanted.filter((t) => existing.includes(t));

  if (toDelete.length > 0) {
    await admin.deleteTopics({ topics: toDelete, timeout: 15000 });
    await waitUntilGone(toDelete);
    console.log(`[${ts()}] deleted: ${toDelete.join(', ')}`);
  }

  await admin.createTopics({
    topics: wanted.map((topic) => ({ topic, numPartitions: PARTITIONS, replicationFactor: 1 })),
    waitForLeaders: true,
    timeout: 15000,
  });
  console.log(`[${ts()}] created (${PARTITIONS} partition each): ${wanted.join(', ')}`);
} finally {
  await admin.disconnect();
}
