package com.solar.Quotation.Enum;

/** What an uploaded file is for. The purpose decides the folder and the permission needed to upload it. */
public enum FilePurpose {
    LOGO("branding"), USER_PHOTO("users");

    private final String folder;

    FilePurpose(String folder) { this.folder = folder; }

    /** Sub-folder under the partner's own folder. Must stay in sync with FilePaths.FOLDERS. */
    public String folder() { return folder; }
}
