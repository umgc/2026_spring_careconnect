package com.careconnect.config;

import com.careconnect.websocket.CallNotificationHandler;
import com.careconnect.websocket.CareConnectWebSocketHandler;
import com.careconnect.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebSocketConfig}.
 *
 * WebSocketConfig registers three WebSocket handlers at distinct endpoints:
 * <ul>
 *   <li>{@code /ws/calls} — call notifications (with SockJS fallback)</li>
 *   <li>{@code /ws/careconnect} — general real-time messaging (with SockJS fallback,
 *       endpoint is configurable via property)</li>
 *   <li>{@code /ws/notifications} — push notifications (plain WebSocket, no SockJS)</li>
 * </ul>
 *
 * All three handler beans and the registry are mocked with Mockito so the test is
 * purely about wiring behaviour — that the correct handler reaches the correct endpoint
 * with the correct options (SockJS, allowed origins). {@link ReflectionTestUtils} injects
 * the {@code @Value}-bound fields ({@code careConnectEndpoint}, {@code allowedOrigins})
 * since no Spring context is running. {@code lenient()} stubs are used for the shared
 * registry mock because not every test verifies every interaction on that mock.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private CallNotificationHandler callNotificationHandler;

    @Mock
    private CareConnectWebSocketHandler careConnectWebSocketHandler;

    @Mock
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Mock
    private WebSocketHandlerRegistry registry;

    @Mock
    private WebSocketHandlerRegistration handlerRegistration;

    private WebSocketConfig webSocketConfig;

    @BeforeEach
    void setUp() throws Exception {
        // Instantiate and wire the config outside of Spring.
        // ReflectionTestUtils injects the three handler fields and the two @Value fields
        // that Spring would normally populate from application properties.
        webSocketConfig = new WebSocketConfig();
        ReflectionTestUtils.setField(webSocketConfig, "callNotificationHandler", callNotificationHandler);
        ReflectionTestUtils.setField(webSocketConfig, "careConnectWebSocketHandler", careConnectWebSocketHandler);
        ReflectionTestUtils.setField(webSocketConfig, "notificationWebSocketHandler", notificationWebSocketHandler);
        ReflectionTestUtils.setField(webSocketConfig, "careConnectEndpoint", "/ws/careconnect");
        ReflectionTestUtils.setField(webSocketConfig, "allowedOrigins", "*");

        // Lenient stubs allow the shared handlerRegistration to be returned for any
        // addHandler call; tests that need per-handler distinctions create their own mocks.
        lenient().when(registry.addHandler(any(), anyString())).thenReturn(handlerRegistration);
        lenient().when(handlerRegistration.setAllowedOrigins(anyString())).thenReturn(handlerRegistration);
    }

    @Test
    void registersCallNotificationHandlerOnWsCalls() throws Exception {
        // Verifies that the call-notification handler is bound to "/ws/calls".
        webSocketConfig.registerWebSocketHandlers(registry);

        verify(registry).addHandler(callNotificationHandler, "/ws/calls");
    }

    @Test
    void registersCareConnectHandlerOnDefaultEndpoint() throws Exception {
        // Verifies that the main chat/messaging handler is bound to "/ws/careconnect"
        // when the configurable endpoint property holds its default value.
        webSocketConfig.registerWebSocketHandlers(registry);

        verify(registry).addHandler(careConnectWebSocketHandler, "/ws/careconnect");
    }

    @Test
    void registersNotificationHandlerOnWsNotifications() throws Exception {
        // Verifies that the push-notification handler is bound to "/ws/notifications".
        webSocketConfig.registerWebSocketHandlers(registry);

        verify(registry).addHandler(notificationWebSocketHandler, "/ws/notifications");
    }

    @Test
    void registersExactlyThreeHandlers() throws Exception {
        // Verifies that no extra handlers are accidentally registered — the total
        // must be exactly three (calls, careconnect, notifications).
        webSocketConfig.registerWebSocketHandlers(registry);

        verify(registry, times(3)).addHandler(any(), anyString());
    }

    @Test
    void setsAllowedOriginsOnAllHandlerRegistrations() throws Exception {
        // Verifies that setAllowedOrigins("*") is called on every registered handler,
        // confirming that all WebSocket endpoints share the same origin policy.
        webSocketConfig.registerWebSocketHandlers(registry);

        verify(handlerRegistration, times(3)).setAllowedOrigins("*");
    }

    @Test
    void callsAndCareConnectHandlersUseSockJs() throws Exception {
        // Verifies that SockJS is enabled for /ws/calls and /ws/careconnect but NOT for
        // /ws/notifications. SockJS provides fallback transports (long-polling, etc.) for
        // environments where native WebSocket is unavailable (e.g. some corporate proxies).

        // /ws/calls and /ws/careconnect both enable SockJS; /ws/notifications does not
        webSocketConfig.registerWebSocketHandlers(registry);

        verify(handlerRegistration, times(2)).withSockJS();
    }

    @Test
    void notificationHandlerDoesNotUseSockJs() throws Exception {
        // Uses separate mocks per handler registration (rather than the shared stub)
        // to individually verify that withSockJS() is called for calls and careconnect
        // but never for notifications, confirming the per-handler SockJS decision.
        final WebSocketHandlerRegistration callsReg = mock(WebSocketHandlerRegistration.class);
        final WebSocketHandlerRegistration careConnectReg = mock(WebSocketHandlerRegistration.class);
        final WebSocketHandlerRegistration notificationsReg = mock(WebSocketHandlerRegistration.class);

        when(registry.addHandler(callNotificationHandler, "/ws/calls")).thenReturn(callsReg);
        when(registry.addHandler(careConnectWebSocketHandler, "/ws/careconnect")).thenReturn(careConnectReg);
        when(registry.addHandler(notificationWebSocketHandler, "/ws/notifications")).thenReturn(notificationsReg);

        when(callsReg.setAllowedOrigins(anyString())).thenReturn(callsReg);
        when(careConnectReg.setAllowedOrigins(anyString())).thenReturn(careConnectReg);
        when(notificationsReg.setAllowedOrigins(anyString())).thenReturn(notificationsReg);

        webSocketConfig.registerWebSocketHandlers(registry);

        verify(callsReg).withSockJS();
        verify(careConnectReg).withSockJS();
        verify(notificationsReg, never()).withSockJS();
    }

    @Test
    void usesCustomCareConnectEndpointWhenSet() throws Exception {
        // Verifies that the careConnectEndpoint @Value field is actually used when
        // registering the handler, rather than being ignored in favour of a hardcoded path.
        ReflectionTestUtils.setField(webSocketConfig, "careConnectEndpoint", "/ws/custom");

        webSocketConfig.registerWebSocketHandlers(registry);

        verify(registry).addHandler(careConnectWebSocketHandler, "/ws/custom");
    }

    @Test
    void usesCustomAllowedOriginsWhenSet() throws Exception {
        // Verifies that a non-wildcard allowedOrigins value is propagated to all three
        // handler registrations, enabling production origin restriction.
        ReflectionTestUtils.setField(webSocketConfig, "allowedOrigins", "https://app.careconnect.com");

        webSocketConfig.registerWebSocketHandlers(registry);

        verify(handlerRegistration, times(3)).setAllowedOrigins("https://app.careconnect.com");
    }

    @Test
    void defaultEndpointIsWsCareconnect() throws Exception {
        // Verifies that the careConnectWebSocketHandler is NOT inadvertently registered
        // on the calls or notifications paths — each handler has one distinct path.
        webSocketConfig.registerWebSocketHandlers(registry);

        verify(registry, never()).addHandler(careConnectWebSocketHandler, "/ws/calls");
        verify(registry, never()).addHandler(careConnectWebSocketHandler, "/ws/notifications");
        verify(registry).addHandler(careConnectWebSocketHandler, "/ws/careconnect");
    }

    @Test
    void eachHandlerRegisteredOnDistinctPath() throws Exception {
        // Comprehensive registration check: every handler is on its own path, and
        // verifyNoMoreInteractions confirms no unexpected addHandler calls were made.
        webSocketConfig.registerWebSocketHandlers(registry);

        verify(registry).addHandler(callNotificationHandler, "/ws/calls");
        verify(registry).addHandler(careConnectWebSocketHandler, "/ws/careconnect");
        verify(registry).addHandler(notificationWebSocketHandler, "/ws/notifications");
        verifyNoMoreInteractions(registry);
    }
}
