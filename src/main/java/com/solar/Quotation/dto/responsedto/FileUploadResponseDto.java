package com.solar.Quotation.dto.responsedto;

public class FileUploadResponseDto {

    /** Store this in the record (logoPath, photoPath), e.g. /files/7/branding/<uuid>.png. Not a URL; see url. */
    private String path;
    /** Authenticated download address, e.g. /api/files/7/branding/<uuid>.png (needs the Bearer token; join with API_BASE). */
    private String url;
    private long size;
    private String contentType;

    public FileUploadResponseDto() { }

    public FileUploadResponseDto(String path, String url, long size, String contentType) {
        this.path = path; this.url = url; this.size = size; this.contentType = contentType;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
