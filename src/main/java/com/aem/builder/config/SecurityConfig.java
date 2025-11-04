package com.aem.builder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",                     // home
                                "/**",                   // allow all URLs inside the app
                                "/artifacts/**",         // artifacts paths
                                "/static/**",            // static resources
                                "/js/**",
                                "/css/**",
                                "/images/**",
                                "/webjars/**"
                        ).permitAll()
                        .anyRequest().permitAll()    // no authentication required
                )
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}