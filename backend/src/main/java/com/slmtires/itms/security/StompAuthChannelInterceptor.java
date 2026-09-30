package com.slmtires.itms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * A browser's native WebSocket constructor cannot set an Authorization header on the handshake
 * request, so /ws is left open in SecurityConfig and authentication instead happens on the STOMP
 * CONNECT frame - a text frame sent over the already-open socket, where an Authorization header is
 * just another STOMP header stompjs can set freely. No CONNECT without a valid bearer token means
 * no session Principal, which means every /user/** destination and every @PreAuthorize'd
 * @MessageMapping (none yet, but the pattern holds) is still independently enforced here, exactly
 * like JwtAuthenticationFilter does for plain HTTP.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith(BEARER_PREFIX)) {
                throw new org.springframework.messaging.MessagingException("Missing bearer token.");
            }
            String token = header.substring(BEARER_PREFIX.length());
            if (!jwtService.isValid(token)) {
                throw new org.springframework.messaging.MessagingException("Invalid or expired token.");
            }
            try {
                String email = jwtService.extractEmail(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                if (!userDetails.isEnabled()) {
                    throw new org.springframework.messaging.MessagingException("Account is inactive.");
                }
                var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                accessor.setUser(authToken);
            } catch (UsernameNotFoundException e) {
                throw new org.springframework.messaging.MessagingException("Account no longer exists.");
            }
        } else if (accessor != null && accessor.getUser() == null
            && !StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            // Every frame after CONNECT (SUBSCRIBE, SEND, ...) must already carry the Principal
            // CONNECT attached above; the socket has no other identity to fall back on.
            throw new org.springframework.messaging.MessagingException("Not authenticated.");
        }
        return message;
    }
}
