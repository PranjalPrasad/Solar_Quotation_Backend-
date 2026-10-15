package com.solar.Quotation.dto.responsedto;

/** POST /api/auth/refresh. The refresh token is rotated: always store the new one. */
public class TokenResponseDto {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;

    public TokenResponseDto() { }

    public TokenResponseDto(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken = accessToken; this.refreshToken = refreshToken; this.expiresIn = expiresIn;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
}
