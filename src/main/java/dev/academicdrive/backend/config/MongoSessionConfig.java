package dev.academicdrive.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;

@Configuration
@EnableMongoHttpSession(collectionName = "sessions")
public class MongoSessionConfig {
    @Bean
    CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookie,
            @Value("${server.servlet.session.cookie.same-site:Lax}") String sameSite) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("ACADEMICDRIVE_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite(sameSite);
        return serializer;
    }
}
