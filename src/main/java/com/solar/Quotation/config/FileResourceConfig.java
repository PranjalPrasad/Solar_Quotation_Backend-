package com.solar.Quotation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Serves uploaded images from disk at GET /files/**. Public on purpose (an img tag cannot send a Bearer token;
 * the logo is needed on the login page and in PDFs). Names are random UUIDs and only images are ever stored.
 * SecurityConfig must permit GET /files/**.
 */
@Configuration
public class FileResourceConfig implements WebMvcConfigurer {

    private final Path root;

    public FileResourceConfig(@Value("${app.file.storage-dir:./uploads}") String storageDir) {
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the upload folder " + root, e);
        }
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}