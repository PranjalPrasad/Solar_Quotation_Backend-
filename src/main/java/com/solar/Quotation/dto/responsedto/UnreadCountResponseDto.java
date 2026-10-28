package com.solar.Quotation.dto.responsedto;

public class UnreadCountResponseDto {

    private long unread;

    public UnreadCountResponseDto() { }
    public UnreadCountResponseDto(long unread) { this.unread = unread; }

    public long getUnread() { return unread; }
    public void setUnread(long unread) { this.unread = unread; }
}
