package com.solar.Quotation.security;

public final class TenantContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private TenantContext() { }

    public static void set(CurrentUser user) { HOLDER.set(user); }
    public static CurrentUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }   // JwtAuthFilter calls this in finally
}