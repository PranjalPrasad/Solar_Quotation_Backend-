package com.solar.Quotation.dto.responsedto;

public class FileUploadResponseDto {

    /** Store this path in the record (logoPath, photoPath). It is stable even if the server address changes. */
    private String path;
    /** Ready-to-use address (path joined with app.file.public-base-url when that is configured). */
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
