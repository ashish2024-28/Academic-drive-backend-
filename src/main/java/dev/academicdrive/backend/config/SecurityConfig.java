
package dev.academicdrive.backend.config;

import dev.academicdrive.backend.auth.GoogleProfileOidcUserService;
import dev.academicdrive.backend.drive.GoogleDriveConnectionService;
import dev.academicdrive.backend.security.EncryptedAuthorizedClientService;
import dev.academicdrive.backend.security.SpaCsrfTokenRequestHandler;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class SecurityConfig {

    private List<String> parseFrontendOrigins(String frontendOrigins) {
        return Arrays.stream(frontendOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GoogleAuthorizationRequestResolver authorizationRequestResolver,
            GoogleProfileOidcUserService oidcUserService,
            EncryptedAuthorizedClientService authorizedClientService,
            GoogleDriveConnectionService driveConnectionService,
            @Value("${app.frontend-origin}") String frontendOrigins,
            @Value("${app.csrf-cookie-domain:}") String csrfCookieDomain,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookie,
            @Value("${server.servlet.session.cookie.same-site:Lax}") String sameSite
    ) throws Exception {

        List<String> allowedFrontendOrigins = parseFrontendOrigins(frontendOrigins);

        // First frontend origin is used for OAuth redirects.
        String frontendOrigin = allowedFrontendOrigins.get(0);

        CookieCsrfTokenRepository csrfTokenRepository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();

        csrfTokenRepository.setCookieCustomizer(cookie -> {
            cookie.path("/")
                    .secure(secureCookie)
                    .sameSite(sameSite)
                    .httpOnly(false);

            if (!csrfCookieDomain.isBlank()) {
                cookie.domain(csrfCookieDomain);
            }
        });

        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                )

                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )

                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**")
                        )
                )

                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint ->
                                endpoint.authorizationRequestResolver(authorizationRequestResolver)
                        )
                        .authorizedClientService(authorizedClientService)
                        .userInfoEndpoint(userInfo ->
                                userInfo.oidcUserService(oidcUserService)
                        )
                        .successHandler((request, response, authentication) -> {

                            if (authentication instanceof OAuth2AuthenticationToken oauthToken
                                    && EncryptedAuthorizedClientService.GOOGLE_DRIVE_REGISTRATION_ID
                                    .equals(oauthToken.getAuthorizedClientRegistrationId())) {

                                try {
                                    if (!(oauthToken.getPrincipal() instanceof OidcUser oidcUser)) {
                                        response.sendRedirect(frontendOrigin + "?drive=setup_failed");
                                        return;
                                    }

                                    driveConnectionService.getStatus(oauthToken, oidcUser);

                                    response.sendRedirect(frontendOrigin + "?drive=connected");

                                } catch (OAuth2AuthorizationException
                                         | RestClientResponseException
                                         | ResponseStatusException exception) {

                                    response.sendRedirect(frontendOrigin + "?drive=setup_failed");
                                }

                                return;
                            }

                            response.sendRedirect(frontendOrigin + "?auth=success");
                        })
                        .failureUrl(frontendOrigin + "?auth=failed")
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("ACADEMICDRIVE_SESSION")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                );

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-origin}") String frontendOrigins
    ) {

        CorsConfiguration configuration = new CorsConfiguration();

        // Allow multiple frontend origins.
        configuration.setAllowedOrigins(parseFrontendOrigins(frontendOrigins));

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Content-Type",
                "X-XSRF-TOKEN",
                "X-Upload-Content-Type",
                "X-Upload-Content-Length"
        ));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClientService
    ) {

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        registrations,
                        authorizedClientService
                );

        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .refreshToken()
                        .build()
        );

        manager.setAuthorizationFailureHandler((exception, principal, attributes) -> {

            if (exception instanceof OAuth2AuthorizationException authorizationException
                    && OAuth2ErrorCodes.INVALID_GRANT.equals(
                    authorizationException.getError().getErrorCode())) {

                authorizedClientService.removeAuthorizedClient(
                        EncryptedAuthorizedClientService.GOOGLE_DRIVE_REGISTRATION_ID,
                        principal.getName()
                );
            }
        });

        return manager;
    }
}
