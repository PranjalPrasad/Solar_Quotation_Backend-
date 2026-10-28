package com.solar.Quotation.dto.requestdto;

public class NotificationFilterRequestDto extends PageFilterRequestDto {

    private boolean unreadOnly;
    private String type;

    public boolean isUnreadOnly() { return unreadOnly; }
    public void setUnreadOnly(boolean unreadOnly) { this.unreadOnly = unreadOnly; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
