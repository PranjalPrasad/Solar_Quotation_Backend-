package com.solar.Quotation.dto.responsedto;

public class ApiErrorResponse {

    private String error;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
