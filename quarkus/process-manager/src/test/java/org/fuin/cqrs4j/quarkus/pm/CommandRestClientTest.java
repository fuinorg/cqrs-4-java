package org.fuin.cqrs4j.quarkus.pm;

import jakarta.enterprise.inject.Instance;
import org.fuin.cqrs4j.core.CommandAuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        testee.config = new CommandQueueConfig("http://localhost:9999", 100, 5);
        testee.authProviders = authProviders;
        testee.httpClient = httpClient;
    }

    @Test
    public void testSuccessfulPostReturnsBody() throws Exception {
        // PREPARE
        final HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("result");
        when(httpClient.<String>send(any(), any())).thenReturn(response);

        // TEST & VERIFY
        assertThat(testee.cmd("MyCommand", null, "{}")).isEqualTo("result");
    }

    @Test
    public void testErrorStatusThrows() throws Exception {
        // PREPARE
        final HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("{\"type\":\"ERROR\"}");
        when(httpClient.<String>send(any(), any())).thenReturn(response);

        // TEST & VERIFY
        assertThatThrownBy(() -> testee.cmd("MyCommand", null, "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
    }

}
