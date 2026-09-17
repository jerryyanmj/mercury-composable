package com.accenture.ordersystem.mongodb.acknowledge;

import org.platformlambda.core.annotations.MainApplication;
import org.platformlambda.core.models.EntryPoint;
import org.platformlambda.core.system.AutoStart;

@MainApplication
public class MainApp implements EntryPoint {
    public static void main(String[] args) {
        AutoStart.main(args);
    }

    @Override
    public void start(String[] args) {
        // stateless Kafka consumer — no REST server
    }
}
