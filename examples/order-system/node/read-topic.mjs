// read-topic.mjs - print the records currently on a topic, headers and all.
//
//   node read-topic.mjs fulfillment.status
//   node read-topic.mjs sor.ack --raw     (skip JSON pretty-printing)
//
// Reads from the beginning under a throwaway consumer group, so it never disturbs the offsets
// of the applications' own groups - you can run it as often as you like, mid-flight or after.

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
const args = process.argv.slice(2);
const RAW = args.includes('--raw');
const topic = args.find((a) => !a.startsWith('--'));

if (!topic) {
  console.error('usage: node read-topic.mjs <topic> [--raw]');
  process.exit(1);
}

// How long to keep listening after the last record before deciding the topic is drained.
const QUIET_MS = 2500;

const kafka = new Kafka({ clientId: 'order-system-reader', brokers: BROKERS, logLevel: 1 });
const consumer = kafka.consumer({ groupId: `reader-${Date.now()}-${process.pid}` });

let count = 0;
let lastSeen = Date.now();

const pretty = (buf) => {
  if (buf === null) return '<null - tombstone>';
  const s = buf.toString();
  if (RAW) return s;
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
      .split('\n')
      .map((l, i) => (i === 0 ? l : '    ' + l))
      .join('\n');
  } catch {
    return s;
  }
};

await consumer.connect();
await consumer.subscribe({ topic, fromBeginning: true });

console.log(`\nreading ${topic} from the beginning ...\n`);

await consumer.run({
  eachMessage: async ({ partition, message }) => {
    count++;
    lastSeen = Date.now();
    const headers = Object.entries(message.headers || {})
      .map(([k, v]) => `${k}=${v?.toString()}`)
      .join('  ');
    const when = new Date(Number(message.timestamp)).toISOString();
    console.log(`--- #${count}  partition ${partition} offset ${message.offset}  ${when}`);
    if (headers) console.log(`    headers: ${headers}`);
    console.log(`    ${pretty(message.value)}\n`);
  },
});

// exit once the topic has gone quiet
const timer = setInterval(async () => {
  if (Date.now() - lastSeen > QUIET_MS) {
    clearInterval(timer);
    console.log(count === 0 ? '(topic is empty)\n' : `${count} record(s)\n`);
    await consumer.disconnect();
    process.exit(0);
  }
}, 500);
