// mongosh setup-manager-db.js
// Creates the order_manager database, collection, and indexes.

db = db.getSiblingDB('order_manager');

db.createCollection('orders');

db.orders.createIndex({ order_id: 1 }, { unique: true, name: 'idx_orders_order_id' });
db.orders.createIndex({ order_status: 1 }, { name: 'idx_orders_status' });
db.orders.createIndex({ 'outbox.sent_at': 1 }, { sparse: true, name: 'idx_orders_unsent_outbox' });

print('order_manager database ready.');
