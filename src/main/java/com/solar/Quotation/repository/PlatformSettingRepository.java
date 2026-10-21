package com.solar.Quotation.repository;

import com.solar.Quotation.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {

    Optional<PlatformSetting> findBySettingKeyAndDeletedFalse(String settingKey);
}
