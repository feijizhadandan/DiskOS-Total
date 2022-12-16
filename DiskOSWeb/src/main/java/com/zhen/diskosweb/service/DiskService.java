package com.zhen.diskosweb.service;

import com.zhen.diskosweb.common.entity.ServiceResult;
import com.zhen.diskosweb.dto.LoginDto;

public interface DiskService {
    ServiceResult loginDisk(LoginDto loginDto);

    ServiceResult diskEntrance(String input);
}
