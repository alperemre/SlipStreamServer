package com.sixsq.slipstream.persistence;

class CloudCredential {
    class ConnectorRef {
        public final String href;
        public ConnectorRef(String href) {
            this.href = href;
        }
    }
    public ConnectorRef connector;
    public String id;
    CloudCredential() {}
    public String getConnectorName() {
        if (null != connector && null != connector.href) {
            String[] parts = connector.href.split("/");
            return parts[1];
        } else {
            return "";
        }
    }
    public String getInstanceID() {
        return id;
    }
}