package com.accenture.ordersystem.mongodb.common;

public final class Events {
    public static final String ORDERS_INBOUND        = "orders.inbound";
    public static final String FULFILLMENT_REQUEST   = "fulfillment.request";
    public static final String FULFILLMENT_TICK      = "fulfillment.tick";
    public static final String FULFILLMENT_ACK       = "fulfillment.ack";
    public static final String FULFILLMENT_STATUS    = "fulfillment.status";
    public static final String SOR_DISPATCH          = "sor.dispatch";
    public static final String SOR_VALIDATION        = "sor.validation";
    public static final String SOR_PREPROCESS        = "sor.preprocess";
    public static final String SOR_PROCESS           = "sor.process";
    public static final String ORDER_STATUS_EXTERNAL = "order.status.external";

    private Events() {}
}
