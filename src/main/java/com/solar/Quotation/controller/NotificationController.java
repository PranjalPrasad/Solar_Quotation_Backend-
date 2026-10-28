package com.solar.Quotation.controller;


import com.solar.Quotation.dto.requestdto.NotificationFilterRequestDto;
import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.NotificationResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.UnreadCountResponseDto;
import com.solar.Quotation.service.NotificationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<PageResponse<NotificationResponseDto>> list(NotificationFilterRequestDto filter) {
        return ApiResponse.ok(service.list(filter));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponseDto> unreadCount() {
        return ApiResponse.ok(service.unreadCount());
    }

    @PatchMapping("/{id}/markRead/read")
    public ApiResponse<UnreadCountResponseDto> markRead(@PathVariable Long id) {
        return ApiResponse.ok(service.markRead(id), "Marked as read");
    }

    @PatchMapping("/markAllRead/read-all")
    public ApiResponse<UnreadCountResponseDto> markAllRead() {
        return ApiResponse.ok(service.markAllRead(), "All notifications marked as read");
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<UnreadCountResponseDto> delete(@PathVariable Long id) {
        return ApiResponse.ok(service.delete(id), "Notification removed");
    }
}
