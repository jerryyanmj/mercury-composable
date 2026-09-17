package com.accenture.ordersystem.mongodb.manager;

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
        // platform auto-starts all @PreLoad functions and the Kafka consumer
    }
}
