package com.zhen.diskosweb.entity.other;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 用来记录某个文件的读写权限信息。存在 INODE 中
 */
@Data
@AllArgsConstructor
public class PermissionRecord implements Serializable {

    private static final long serialVersionUID = -660190320864574L;
    // 拥有者
    String ownerName;
    // 拥有者权限
    int ownerPermission;
    // 其他人的权限
    int otherPermission;

}
