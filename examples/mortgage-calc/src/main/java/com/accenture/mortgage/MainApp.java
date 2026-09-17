/*
    Copyright 2018-2026 Accenture Technology
    Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.accenture.mortgage;

import org.platformlambda.core.annotations.MainApplication;
import org.platformlambda.core.models.EntryPoint;
import org.platformlambda.core.system.AutoStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap for the Mortgage Amortization Calculator.
 * The calculation lives in the graph model (classpath:/graph/mortgage-calc.json); this
 * app only wires the graph runtime and exposes it at POST /api/graph/mortgage-calc.
 */
@MainApplication
public class MainApp implements EntryPoint {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        AutoStart.main(args);
    }

    @Override
    public void start(String[] args) {
        log.info("Mortgage calculator ready - POST /api/graph/mortgage-calc");
    }
}
