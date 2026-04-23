package com.innowise.gateway.filter;


public final class GatewayFilterOrders {

    public static final int REQUEST_ID = -100;

    public static final int JWT_AUTHENTICATION = -50;

    public static final int HEADER_ENRICHMENT = -40;

    public static final int LOGGING = -2;

    public static final int IDEMPOTENCY = -199;

    private GatewayFilterOrders() {
    }
}
