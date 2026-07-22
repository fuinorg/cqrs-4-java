package org.fuin.cqrs4j.quarkus.pm;

import jakarta.enterprise.inject.Instance;
import org.fuin.cqrs4j.core.TransientCommandDeliveryException;
import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link CommandRestClient} class with a mocked {@link HttpClient}.
 */
@SuppressWarnings("unchecked")
public class CommandRestClientTest {

    private HttpClient httpClient;

    private Instance<CommandAuthProvider> authProviders;

    private CommandRestClient testee;

    @BeforeEach
    public void setUp() {
        httpClient = mock(HttpClient.class);
        authProviders = mock(Instance.class);
        when(authProviders.isResolvable()).thenReturn(false);
        testee = new CommandRestClient();
        testee.config = new CommandQueueConfig("http://localhost:9999", 100, 5, 1000, 1000, 30000, 4, 0.5);
        testee.authProviders = authProviders;
        testee.httpClient = httpClient;
    }

    @Test
    public void testSuccessfulPostSendsContentTypeVerbatim() throws Exception {
        // PREPARE
        final HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("result");
        when(httpClient.<String>send(any(), any())).thenReturn(response);

        // TEST: a non-JSON content type must reach the endpoint unchanged (no "application/json" assumption)
        assertThat(testee.cmd("MyCommand", "application/xml;version=1", "<cmd/>")).isEqualTo("result");

        // VERIFY: the Content-Type header equals what was passed
        final ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("Content-Type")).contains("application/xml;version=1");
    }

    @Test
    public void testErrorStatusThrows() throws Exception {
        // PREPARE
        final HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("{\"type\":\"ERROR\"}");
        when(httpClient.<String>send(any(), any())).thenReturn(response);

        // TEST & VERIFY
        assertThatThrownBy(() -> testee.cmd("MyCommand", "application/json", "{}"))
                .isInstanceOf(TransientCommandDeliveryException.class)
                .hasMessageContaining("500");
    }

}
