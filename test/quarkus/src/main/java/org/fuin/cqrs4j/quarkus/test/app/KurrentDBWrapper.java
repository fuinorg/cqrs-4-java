package org.fuin.cqrs4j.quarkus.test.app;

import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBProjectionManagementClient;
import org.fuin.cqrs4j.quarkus.base.EventstoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class that wraps the KurrentDB clients to be compliant with required CDI default constructor.
 */
public class KurrentDBWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(KurrentDBWrapper.class);

    private KurrentDBClient client;

    private KurrentDBProjectionManagementClient projectionManagementClient;

    protected KurrentDBWrapper() {
    }

    public KurrentDBWrapper(final EventstoreConfig config) {
        client = kurrentDBClient(config);
        projectionManagementClient = kurrentDBProjectionManagementClient(config);
    }

    public KurrentDBClient getClient() {
        return client;
    }

    public KurrentDBProjectionManagementClient getProjectionManagementClient() {
        return projectionManagementClient;
    }

    public void shutdown() {
        try {
            client.shutdown();
        } catch (final RuntimeException ex) {
            LOG.error("Failed to close client", ex);
        }
        try {
            projectionManagementClient.shutdown();
        } catch (final RuntimeException ex) {
            LOG.error("Failed to close projectionManagementClient", ex);
        }
    }

    private static KurrentDBClient kurrentDBClient(final EventstoreConfig config) {
        final KurrentDBClientSettings setts = KurrentDBClientSettings.builder()
                .addHost(config.getHost(), config.getPort())
                .defaultCredentials("admin", "changeit") // Just for test
                .tls(false)
                .buildConnectionSettings();
        return KurrentDBClient.create(setts);
    }

    private static KurrentDBProjectionManagementClient kurrentDBProjectionManagementClient(final EventstoreConfig config) {
        final KurrentDBClientSettings settings = KurrentDBClientSettings.builder()
                .addHost(config.getHost(), config.getPort())
                .defaultCredentials("admin", "changeit") // Just for test
                .tls(config.isTls())
                .buildConnectionSettings();
        return KurrentDBProjectionManagementClient.create(settings);
    }


}
