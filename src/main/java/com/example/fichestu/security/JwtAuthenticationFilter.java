package com.example.fichestu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.service.JwtTokenRevocationService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JwtTokenRevocationService jwtTokenRevocationService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        JwtTokenRevocationService jwtTokenRevocationService,
        UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.jwtTokenRevocationService = jwtTokenRevocationService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            if (jwtTokenRevocationService.isRevoked(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            jwtService.parseClaims(token)
                .flatMap(claims -> userRepository.findById(claims.userId()))
                .ifPresent(user -> {
                    if (SecurityContextHolder.getContext().getAuthentication() == null) {
                        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                            user.getUserId(),
                            user.getEmail(),
                            user.getUsername(),
                            normalizeRole(user.getRole())
                        );
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            authenticatedUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + authenticatedUser.role()))
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                });
        }

        filterChain.doFilter(request, response);
    }

    private String normalizeRole(String role) {
        return role == null || role.isBlank() ? "USER" : role.trim().toUpperCase();
    }
}
