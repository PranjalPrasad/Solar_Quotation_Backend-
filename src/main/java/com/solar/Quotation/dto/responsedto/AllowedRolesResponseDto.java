package com.solar.Quotation.dto.responsedto;

import java.util.List;

public class AllowedRolesResponseDto {

    private Long partnerId;
    /** Har assignable role, allowed flag ke saath (UI checklist ke liye). */
    private List<Item> roles;

    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public List<Item> getRoles() { return roles; }
    public void setRoles(List<Item> roles) { this.roles = roles; }

    public static class Item {
        private Long id;
        private String code;
        private String name;
        private boolean allowed;
        /** true = hamesha allowed (CHANNEL_PARTNER_ADMIN); UI me disabled dikhao. */
        private boolean locked;

        public Item() { }
        public Item(Long id, String code, String name, boolean allowed, boolean locked) {
            this.id = id; this.code = code; this.name = name; this.allowed = allowed; this.locked = locked;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isAllowed() { return allowed; }
        public void setAllowed(boolean allowed) { this.allowed = allowed; }
        public boolean isLocked() { return locked; }
        public void setLocked(boolean locked) { this.locked = locked; }
    }
}