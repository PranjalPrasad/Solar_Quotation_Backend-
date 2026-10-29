package com.solar.Quotation.repository;

import com.solar.Quotation.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {

    /** Settings are per partner: a partner only ever reads or writes its own row. */
    Optional<PlatformSetting> findByChannelPartnerIdAndSettingKeyAndDeletedFalse(Long channelPartnerId, String settingKey);

    List<PlatformSetting> findByChannelPartnerIdAndDeletedFalse(Long channelPartnerId);
}