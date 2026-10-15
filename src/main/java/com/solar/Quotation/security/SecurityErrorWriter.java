package com.solar.Quotation.security;

import com.solar.Quotation.dto.responsedto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** Writes the standard error envelope for failures that happen before a controller is reached. */
public final class SecurityErrorWriter {

    private SecurityErrorWriter() { }

    public static void write(ObjectMapper mapper, HttpServletResponse response, int status,
                             String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ApiResponse.error(message, errorCode, null)));
    }
}
