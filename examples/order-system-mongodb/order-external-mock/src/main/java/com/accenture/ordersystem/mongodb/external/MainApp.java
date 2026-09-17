package com.accenture.ordersystem.mongodb.external;

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
        // mock SoR and external party
    }
}
