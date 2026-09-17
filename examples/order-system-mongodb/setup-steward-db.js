// mongosh setup-steward-db.js
// Creates the order_steward database, collection, and indexes.

db = db.getSiblingDB('order_steward');

db.createCollection('fulfillments');

db.fulfillments.createIndex({ fulfillment_id: 1 }, { unique: true, name: 'idx_fulfillments_id' });
db.fulfillments.createIndex({ lifecycle_state: 1 }, { name: 'idx_fulfillments_state' });
db.fulfillments.createIndex({ 'outbox.sent_at': 1 }, { sparse: true, name: 'idx_fulfillments_unsent_outbox' });

print('order_steward database ready.');
